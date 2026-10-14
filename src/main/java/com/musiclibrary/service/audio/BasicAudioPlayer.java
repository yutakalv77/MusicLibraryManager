package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import javazoom.jl.player.Player;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Production audio playback engine leveraging JLayer for MP3 decoding.
 * Operates purely on background worker threads without blocking the Swing EDT,
 * dispatches all listener events to the EDT, and supports real-time hardware/software volume adjustment.
 */
public class BasicAudioPlayer implements AudioPlayer {

    private static final float DEFAULT_VOLUME = 0.7f;
    private static final long PROGRESS_NOTIFICATION_INTERVAL_MS = 250L;

    private final Object stateLock = new Object();
    private final Object pauseLock = new Object();
    private final List<PlayerListener> listeners = new CopyOnWriteArrayList<>();

    private volatile PlaybackState state = PlaybackState.STOPPED;
    private volatile Track currentTrack = null;
    private volatile float volume = DEFAULT_VOLUME;
    private volatile long currentPositionSeconds = 0;

    private volatile boolean stopRequested = false;
    private volatile boolean paused = false;
    private volatile Long pendingSeekSeconds = null;

    private Thread playbackThread = null;
    private VolumeAudioDevice activeAudioDevice = null;
    private Player activeJlayerPlayer = null;
    private InputStream activeInputStream = null;

    public BasicAudioPlayer() {
        this(DEFAULT_VOLUME);
    }

    public BasicAudioPlayer(float initialVolume) {
        setVolume(initialVolume);
    }

    @Override
    public void play(Track track) {
        if (track == null) {
            throw new IllegalArgumentException("Track cannot be null");
        }

        synchronized (stateLock) {
            stopInternal();

            this.currentTrack = track;
            this.currentPositionSeconds = 0;
            this.pendingSeekSeconds = null;
            this.stopRequested = false;
            this.paused = false;

            playbackThread = new Thread(() -> runPlayback(track), "AudioPlayer-Worker");
            playbackThread.setDaemon(true);
            playbackThread.start();
        }
    }

    @Override
    public void pause() {
        synchronized (stateLock) {
            if (state == PlaybackState.PLAYING) {
                paused = true;
                transitionState(PlaybackState.PAUSED);
            }
        }
    }

    @Override
    public void resume() {
        synchronized (stateLock) {
            if (state == PlaybackState.PAUSED) {
                paused = false;
                synchronized (pauseLock) {
                    pauseLock.notifyAll();
                }
                transitionState(PlaybackState.PLAYING);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (stateLock) {
            stopInternal();
        }
    }

    private void stopInternal() {
        stopRequested = true;
        paused = false;
        pendingSeekSeconds = null;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }

        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                playbackThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }

        cleanupPlaybackResources();
        this.currentPositionSeconds = 0;

        if (state != PlaybackState.STOPPED) {
            transitionState(PlaybackState.STOPPED);
        }
    }

    @Override
    public void seekTo(long seconds) {
        synchronized (stateLock) {
            if (state == PlaybackState.STOPPED || currentTrack == null) {
                return;
            }
            long totalSeconds = getTotalDurationSeconds();
            long clampedSeconds = totalSeconds > 0
                    ? Math.max(0, Math.min(totalSeconds, seconds))
                    : Math.max(0, seconds);

            this.currentPositionSeconds = clampedSeconds;
            this.pendingSeekSeconds = clampedSeconds;

            // Dispatch instant progress update to EDT
            dispatch(l -> l.onProgressUpdate(clampedSeconds, totalSeconds));

            // Wake up playback thread if paused so it prepares the stream at new position while keeping PAUSED state
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        VolumeAudioDevice device = this.activeAudioDevice;
        if (device != null) {
            device.setVolume(this.volume);
        }
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public PlaybackState getState() {
        return state;
    }

    @Override
    public Track getCurrentTrack() {
        return currentTrack;
    }

    @Override
    public long getCurrentPositionSeconds() {
        return currentPositionSeconds;
    }

    @Override
    public long getTotalDurationSeconds() {
        Track track = this.currentTrack;
        return track != null ? track.getDurationSeconds() : 0;
    }

    @Override
    public boolean isPlaying() {
        return state == PlaybackState.PLAYING;
    }

    @Override
    public void addPlayerListener(PlayerListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removePlayerListener(PlayerListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public void close() {
        stop();
        listeners.clear();
    }

    private void runPlayback(Track track) {
        transitionState(PlaybackState.PLAYING);
        dispatch(l -> l.onTrackStarted(track));

        boolean naturalCompletion = false;
        Throwable errorEncountered = null;

        try {
            String filePathStr = track.getFilePath();
            if (filePathStr == null || filePathStr.isBlank()) {
                throw new IllegalArgumentException("Track filePath is null or blank");
            }

            Path audioPath = Paths.get(filePathStr);
            if (!Files.exists(audioPath)) {
                throw new FileNotFoundException("Audio file does not exist: " + filePathStr);
            }
            if (Files.size(audioPath) == 0) {
                throw new IllegalArgumentException("Audio file is empty: " + filePathStr);
            }

            long totalSeconds = track.getDurationSeconds();
            long lastProgressReportMs = System.currentTimeMillis();
            long seekOffsetSeconds = 0;
            int framesDecoded = 0;

            initJlayerPlayer(audioPath, 0, totalSeconds);

            while (!stopRequested) {
                Long targetSeek = pendingSeekSeconds;
                if (targetSeek != null) {
                    pendingSeekSeconds = null;
                    seekOffsetSeconds = targetSeek;
                    this.currentPositionSeconds = seekOffsetSeconds;

                    if (totalSeconds > 0 && seekOffsetSeconds >= totalSeconds) {
                        naturalCompletion = true;
                        break;
                    }

                    initJlayerPlayer(audioPath, seekOffsetSeconds, totalSeconds);
                    lastProgressReportMs = System.currentTimeMillis();
                }

                // Handle paused state
                if (paused) {
                    synchronized (pauseLock) {
                        while (paused && !stopRequested && pendingSeekSeconds == null) {
                            pauseLock.wait();
                        }
                        if (pendingSeekSeconds != null) {
                            continue;
                        }
                    }
                }

                if (stopRequested) {
                    break;
                }

                // Decode and render single frame (~26ms)
                boolean hasMoreFrames = activeJlayerPlayer.play(1);
                if (!hasMoreFrames) {
                    if (framesDecoded == 0 && seekOffsetSeconds == 0) {
                        throw new javazoom.jl.decoder.JavaLayerException("No playable audio frames found in file: " + filePathStr);
                    }
                    naturalCompletion = true;
                    break;
                }
                framesDecoded++;

                // Update position
                int posMs = activeAudioDevice.getPosition();
                long curSec = totalSeconds > 0
                        ? Math.min(totalSeconds, seekOffsetSeconds + (posMs / 1000L))
                        : seekOffsetSeconds + (posMs / 1000L);
                this.currentPositionSeconds = curSec;

                long now = System.currentTimeMillis();
                if (now - lastProgressReportMs >= PROGRESS_NOTIFICATION_INTERVAL_MS) {
                    lastProgressReportMs = now;
                    dispatch(l -> l.onProgressUpdate(curSec, totalSeconds));
                }
            }

        } catch (InterruptedException e) {
            // Thread interrupted on stop request
        } catch (Throwable t) {
            if (!stopRequested) {
                errorEncountered = t;
            }
        } finally {
            cleanupPlaybackResources();

            synchronized (stateLock) {
                this.state = PlaybackState.STOPPED;
            }
            dispatch(l -> l.onStateChanged(PlaybackState.STOPPED));

            if (errorEncountered != null) {
                Throwable err = errorEncountered;
                dispatch(l -> l.onError(err));
            } else if (naturalCompletion && !stopRequested) {
                dispatch(l -> l.onTrackFinished(track));
            }
        }
    }

    private void initJlayerPlayer(Path audioPath, long seekOffsetSeconds, long totalSeconds) throws Exception {
        cleanupPlaybackResources();

        activeInputStream = new BufferedInputStream(Files.newInputStream(audioPath));
        if (seekOffsetSeconds > 0 && totalSeconds > 0) {
            long fileSize = Files.size(audioPath);
            long skipBytes = (fileSize * seekOffsetSeconds) / totalSeconds;
            long skipped = 0;
            while (skipped < skipBytes) {
                long n = activeInputStream.skip(skipBytes - skipped);
                if (n <= 0) {
                    break;
                }
                skipped += n;
            }
        }
        activeAudioDevice = new VolumeAudioDevice(this.volume);
        activeJlayerPlayer = new Player(activeInputStream, activeAudioDevice);
    }

    private void cleanupPlaybackResources() {
        if (activeJlayerPlayer != null) {
            try {
                activeJlayerPlayer.close();
            } catch (Exception ignored) {}
            activeJlayerPlayer = null;
        }

        if (activeAudioDevice != null) {
            try {
                activeAudioDevice.close();
            } catch (Exception ignored) {}
            activeAudioDevice = null;
        }

        if (activeInputStream != null) {
            try {
                activeInputStream.close();
            } catch (Exception ignored) {}
            activeInputStream = null;
        }
    }

    private void transitionState(PlaybackState newState) {
        this.state = newState;
        dispatch(l -> l.onStateChanged(newState));
    }

    private void dispatch(Consumer<PlayerListener> event) {
        Runnable action = () -> {
            for (PlayerListener listener : listeners) {
                try {
                    event.accept(listener);
                } catch (Throwable ignored) {
                }
            }
        };

        if (SwingUtilities.isEventDispatchThread() || GraphicsEnvironment.isHeadless()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
