package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * High-performance audio player engine leveraging VLCJ and LibVLC.
 * Supports multi-format playback (MP3, FLAC, AAC, ALAC, WAV, OGG, etc.) with hardware acceleration
 * and low-latency seeking. Dispatches all playback lifecycle callbacks safely to the Swing EDT.
 */
@SuppressWarnings("this-escape")
public class VlcjAudioPlayer implements AudioPlayer {

    private static final System.Logger LOGGER = System.getLogger(VlcjAudioPlayer.class.getName());
    private static final float DEFAULT_VOLUME = 0.7f;

    /**
     * Audio-only native VLC initialization flags.
     */
    static final String[] VLC_ARGS = {
        "--no-video",
        "--quiet",
        "--no-plugins-cache"
    };

    private final Object stateLock = new Object();
    private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();

    private final AudioPlayerComponent audioPlayerComponent;
    private final MediaPlayer mediaPlayer;
    private final MediaPlayerEventAdapter mediaPlayerEventListener;

    private volatile PlaybackState state = PlaybackState.STOPPED;
    private volatile Track currentTrack = null;
    private volatile float volume = DEFAULT_VOLUME;
    private volatile long currentPositionSeconds = 0;
    private volatile boolean trackStartedDispatched = false;
    private volatile boolean closed = false;

    /**
     * Constructs a VlcjAudioPlayer with default audio settings and volume (0.7).
     */
    public VlcjAudioPlayer() {
        this(createDefaultComponent(), DEFAULT_VOLUME);
    }

    /**
     * Constructs a VlcjAudioPlayer with the specified initial volume.
     *
     * @param initialVolume initial volume in range [0.0f, 1.0f]
     */
    public VlcjAudioPlayer(float initialVolume) {
        this(createDefaultComponent(), initialVolume);
    }

    /**
     * Constructs a VlcjAudioPlayer using the provided AudioPlayerComponent.
     * Facilitates dependency injection for testing.
     *
     * @param audioPlayerComponent VLCJ AudioPlayerComponent instance
     */
    public VlcjAudioPlayer(AudioPlayerComponent audioPlayerComponent) {
        this(audioPlayerComponent, DEFAULT_VOLUME);
    }

    /**
     * Constructs a VlcjAudioPlayer using the provided AudioPlayerComponent and initial volume.
     *
     * @param audioPlayerComponent VLCJ AudioPlayerComponent instance
     * @param initialVolume        initial volume in range [0.0f, 1.0f]
     */
    public VlcjAudioPlayer(AudioPlayerComponent audioPlayerComponent, float initialVolume) {
        this(audioPlayerComponent, audioPlayerComponent != null ? audioPlayerComponent.mediaPlayer() : null, initialVolume);
    }

    /**
     * Package-private constructor allowing explicit injection of AudioPlayerComponent and MediaPlayer
     * for unit testing with mocks/stubs.
     *
     * @param audioPlayerComponent component instance (may be null if mediaPlayer provided)
     * @param mediaPlayer          MediaPlayer instance
     * @param initialVolume        initial volume in range [0.0f, 1.0f]
     */
    VlcjAudioPlayer(AudioPlayerComponent audioPlayerComponent, MediaPlayer mediaPlayer, float initialVolume) {
        this.audioPlayerComponent = audioPlayerComponent;
        this.mediaPlayer = mediaPlayer != null ? mediaPlayer : (audioPlayerComponent != null ? audioPlayerComponent.mediaPlayer() : null);
        this.mediaPlayerEventListener = new VlcjPlayerEventListener();
        if (this.mediaPlayer != null) {
            try {
                if (this.mediaPlayer.events() != null) {
                    this.mediaPlayer.events().addMediaPlayerEventListener(this.mediaPlayerEventListener);
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to register VLC listener during construction", e);
            }
        }
        setVolume(initialVolume);
    }

    private static AudioPlayerComponent createDefaultComponent() {
        return new AudioPlayerComponent(new MediaPlayerFactory(VLC_ARGS));
    }

    @Override
    public void play(Track track) {
        if (track == null) {
            throw new IllegalArgumentException("Track cannot be null");
        }

        synchronized (stateLock) {
            if (closed) {
                throw new IllegalStateException("AudioPlayer is closed");
            }

            this.currentTrack = track;
            this.currentPositionSeconds = 0;
            this.trackStartedDispatched = false;
            this.state = PlaybackState.STOPPED;

            if (mediaPlayer != null) {
                try {
                    int intVolume = Math.round(volume * 100.0f);
                    if (mediaPlayer.audio() != null) {
                        mediaPlayer.audio().setVolume(intVolume);
                        mediaPlayer.audio().setMute(volume <= 0.001f);
                    }

                    boolean started = (mediaPlayer.media() != null) && mediaPlayer.media().play(track.getFilePath());
                    if (!started) {
                        LOGGER.log(System.Logger.Level.WARNING, "VLC media play returned false for path: " + track.getFilePath());
                        transitionState(PlaybackState.STOPPED);
                        dispatchToEdt(l -> l.onError(new RuntimeException("Failed to start VLC playback for: " + track.getFilePath())));
                    }
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING, "Exception starting VLC playback", e);
                    transitionState(PlaybackState.STOPPED);
                    dispatchToEdt(l -> l.onError(e));
                }
            }
        }
    }

    @Override
    public void pause() {
        synchronized (stateLock) {
            if (state == PlaybackState.PLAYING) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.controls() != null) {
                            mediaPlayer.controls().pause();
                        }
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING, "Failed to pause VLC player", e);
                    }
                }
                transitionState(PlaybackState.PAUSED);
            }
        }
    }

    @Override
    public void resume() {
        synchronized (stateLock) {
            if (state == PlaybackState.PAUSED) {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.controls() != null) {
                            mediaPlayer.controls().play();
                        }
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING, "Failed to resume VLC player", e);
                    }
                }
                transitionState(PlaybackState.PLAYING);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (stateLock) {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.controls() != null) {
                        mediaPlayer.controls().stop();
                    }
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING, "Failed to stop VLC player", e);
                }
            }
            this.currentPositionSeconds = 0;
            transitionState(PlaybackState.STOPPED);
        }
    }

    @Override
    public void seekTo(long seconds) {
        long target = Math.max(0, seconds);
        this.currentPositionSeconds = target;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.controls() != null) {
                    mediaPlayer.controls().setTime(target * 1000L);
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to seek VLC player to: " + target + "s", e);
            }
        }
    }

    @Override
    public boolean isSeekable() {
        return true;
    }

    @Override
    public void setVolume(float volume) {
        float clamped = Math.max(0.0f, Math.min(1.0f, volume));
        this.volume = clamped;
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.audio() != null) {
                    int intVolume = Math.round(clamped * 100.0f);
                    mediaPlayer.audio().setVolume(intVolume);
                    mediaPlayer.audio().setMute(clamped <= 0.001f);
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to set VLC volume", e);
            }
        }
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    @Override
    public PlaybackState getState() {
        return this.state;
    }

    @Override
    public Track getCurrentTrack() {
        return this.currentTrack;
    }

    @Override
    public long getCurrentPositionSeconds() {
        return this.currentPositionSeconds;
    }

    @Override
    public long getTotalDurationSeconds() {
        try {
            if (mediaPlayer != null && mediaPlayer.status() != null) {
                long lenMs = mediaPlayer.status().length();
                if (lenMs > 0) {
                    return lenMs / 1000L;
                }
            }
        } catch (Exception ignored) {
            // Native status query error; fall back to track metadata
        }
        Track track = this.currentTrack;
        return track != null ? track.getDurationSeconds() : 0;
    }

    @Override
    public boolean isPlaying() {
        return state == PlaybackState.PLAYING;
    }

    @Override
    public void addPlayerListener(PlayerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removePlayerListener(PlayerListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        synchronized (stateLock) {
            if (closed) {
                return;
            }
            closed = true;

            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.controls() != null) {
                        mediaPlayer.controls().stop();
                    }
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Error stopping VLC player during close", e);
                }
                if (mediaPlayerEventListener != null) {
                    try {
                        if (mediaPlayer.events() != null) {
                            mediaPlayer.events().removeMediaPlayerEventListener(mediaPlayerEventListener);
                        }
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Error removing VLC listener during close", e);
                    }
                }
            }

            listeners.clear();
            state = PlaybackState.STOPPED;
            currentTrack = null;

            if (audioPlayerComponent != null) {
                try {
                    audioPlayerComponent.release();
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING, "Error releasing AudioPlayerComponent", e);
                }
            }
        }
    }

    private void transitionState(PlaybackState newState) {
        PlaybackState oldState = this.state;
        if (oldState != newState) {
            this.state = newState;
            dispatchToEdt(l -> l.onStateChanged(newState));
        }
    }

    private void dispatchToEdt(Consumer<PlayerListener> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            for (PlayerListener listener : listeners) {
                try {
                    action.accept(listener);
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.WARNING, "Error dispatching player event", ex);
                }
            }
        } else {
            SwingUtilities.invokeLater(() -> {
                for (PlayerListener listener : listeners) {
                    try {
                        action.accept(listener);
                    } catch (Exception ex) {
                        LOGGER.log(System.Logger.Level.WARNING, "Error dispatching player event", ex);
                    }
                }
            });
        }
    }

    AudioPlayerComponent getAudioPlayerComponent() {
        return audioPlayerComponent;
    }

    MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    MediaPlayerEventAdapter getMediaPlayerEventListener() {
        return mediaPlayerEventListener;
    }

    private class VlcjPlayerEventListener extends MediaPlayerEventAdapter {
        @Override
        public void playing(MediaPlayer mp) {
            Track startedTrack = null;
            synchronized (stateLock) {
                transitionState(PlaybackState.PLAYING);
                if (!trackStartedDispatched) {
                    trackStartedDispatched = true;
                    startedTrack = currentTrack;
                }
            }
            if (startedTrack != null) {
                Track finalTrack = startedTrack;
                dispatchToEdt(l -> l.onTrackStarted(finalTrack));
            }
        }

        @Override
        public void paused(MediaPlayer mp) {
            synchronized (stateLock) {
                transitionState(PlaybackState.PAUSED);
            }
        }

        @Override
        public void stopped(MediaPlayer mp) {
            synchronized (stateLock) {
                currentPositionSeconds = 0;
                transitionState(PlaybackState.STOPPED);
            }
        }

        @Override
        public void finished(MediaPlayer mp) {
            Track finishedTrack;
            synchronized (stateLock) {
                finishedTrack = currentTrack;
                currentPositionSeconds = 0;
                transitionState(PlaybackState.STOPPED);
            }
            if (finishedTrack != null) {
                dispatchToEdt(l -> l.onTrackFinished(finishedTrack));
            }
        }

        @Override
        public void timeChanged(MediaPlayer mp, long newTime) {
            long currentSec = Math.max(0, newTime / 1000L);
            currentPositionSeconds = currentSec;
            long totalSec = getTotalDurationSeconds();
            dispatchToEdt(l -> l.onProgressUpdate(currentSec, totalSec));
        }

        @Override
        public void error(MediaPlayer mp) {
            synchronized (stateLock) {
                currentPositionSeconds = 0;
                transitionState(PlaybackState.STOPPED);
            }
            dispatchToEdt(l -> l.onError(new RuntimeException("VLC playback error occurred")));
        }
    }
}
