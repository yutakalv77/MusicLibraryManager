package com.musiclibrary.controller;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.PlayQueue;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.ui.panel.ControlBarPanel;
import static com.musiclibrary.util.SwingUtils.runOnEdt;

import javax.swing.event.ChangeListener;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Controller mediating audio playback operations between AudioPlayer,
 * PlayQueue, ControlBarPanel UI components, and table selection actions.
 */
public class PlayerController implements AutoCloseable {

    private static final long DEFAULT_SKIP_SECONDS = 5L;

    private final AudioPlayer audioPlayer;
    private final ControlBarPanel controlBarPanel;
    private final PlayQueue queue;
    private final List<QueueChangeListener> queueListeners = new CopyOnWriteArrayList<>();

    private final PlayerListener playerListener;
    private final ActionListener playPauseListener;
    private final ActionListener prevListener;
    private final ActionListener nextListener;
    private final ChangeListener volumeChangeListener;

    private Runnable onNextAction;
    private Runnable onPreviousAction;
    private Consumer<Throwable> errorHandler;

    private boolean isUpdatingVolume = false;
    private volatile boolean disposed = false;

    public PlayerController(AudioPlayer audioPlayer, ControlBarPanel controlBarPanel) {
        this(audioPlayer, controlBarPanel, new PlayQueue());
    }

    public PlayerController(AudioPlayer audioPlayer, ControlBarPanel controlBarPanel, PlayQueue playQueue) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer cannot be null");
        this.controlBarPanel = controlBarPanel;
        this.queue = (playQueue != null) ? playQueue : new PlayQueue();

        // 1. Create AudioPlayer listener to reflect engine events into ControlBarPanel LCD
        this.playerListener = new PlayerListener() {
            @Override
            public void onStateChanged(PlaybackState newState) {
                runOnEdt(() -> {
                    if (controlBarPanel != null && !disposed) {
                        controlBarPanel.updatePlaybackState(newState);
                    }
                });
            }

            @Override
            public void onTrackStarted(Track track) {
                runOnEdt(() -> {
                    if (controlBarPanel != null && !disposed && track != null) {
                        controlBarPanel.updateTrackInfo(track);
                    }
                });
            }

            @Override
            public void onProgressUpdate(long currentSeconds, long totalSeconds) {
                runOnEdt(() -> {
                    if (controlBarPanel != null && !disposed) {
                        controlBarPanel.updateProgress(currentSeconds, totalSeconds);
                    }
                });
            }

            @Override
            public void onTrackFinished(Track track) {
                runOnEdt(() -> {
                    if (!disposed) {
                        if (onNextAction != null) {
                            onNextAction.run();
                            return;
                        }
                        if (queue.hasNext()) {
                            Track nextTrack = queue.next();
                            if (nextTrack != null) {
                                audioPlayer.play(nextTrack);
                                return;
                            }
                        }
                        audioPlayer.stop();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnEdt(() -> {
                    if (!disposed) {
                        if (errorHandler != null) {
                            errorHandler.accept(error);
                        }
                    }
                });
            }
        };

        this.audioPlayer.addPlayerListener(this.playerListener);

        // 2. Wire ControlBarPanel actions if provided
        if (this.controlBarPanel != null) {
            this.playPauseListener = e -> togglePlayPause();
            this.prevListener = e -> previous();
            this.nextListener = e -> next();
            this.volumeChangeListener = e -> {
                if (!isUpdatingVolume && !disposed) {
                    int percent = controlBarPanel.getVolumeSlider().getValue();
                    setVolumePercent(percent);
                }
            };

            this.controlBarPanel.getPlayPauseButton().addActionListener(this.playPauseListener);
            this.controlBarPanel.getPrevButton().addActionListener(this.prevListener);
            this.controlBarPanel.getNextButton().addActionListener(this.nextListener);
            this.controlBarPanel.getVolumeSlider().addChangeListener(this.volumeChangeListener);
            this.controlBarPanel.setSeekListener(this::seekTo);
            this.controlBarPanel.setShuffleToggleListener(this::toggleShuffle);
            this.controlBarPanel.setRepeatCycleListener(this::cycleRepeatMode);

            // Sync initial state
            this.controlBarPanel.updatePlaybackState(this.audioPlayer.getState());
            this.controlBarPanel.updateShuffleState(this.queue.isShuffle());
            this.controlBarPanel.updateRepeatMode(this.queue.getRepeatMode());
            syncVolumeSliderToPlayer();
        } else {
            this.playPauseListener = null;
            this.prevListener = null;
            this.nextListener = null;
            this.volumeChangeListener = null;
        }
    }

    /**
     * Begins playback of track with surrounding contextual tracks loaded into queue.
     *
     * @param track         track to play
     * @param contextTracks list of context tracks (e.g. from table or playlist)
     */
    public void play(Track track, List<Track> contextTracks) {
        if (track == null) {
            return;
        }
        if (contextTracks != null && !contextTracks.isEmpty()) {
            queue.setTracks(contextTracks, track);
        } else {
            queue.setTracks(List.of(track), 0);
        }
        audioPlayer.play(track);
        notifyQueueListeners(l -> l.onQueueUpdated(queue));
    }

    /**
     * Begins playback of the specified track.
     *
     * @param track track to play
     */
    public void play(Track track) {
        if (track == null) {
            return;
        }
        if (!queue.getPlaybackOrder().contains(track)) {
            queue.setTracks(List.of(track), 0);
        } else {
            queue.setTracks(queue.getPlaybackOrder(), track);
        }
        audioPlayer.play(track);
        notifyQueueListeners(l -> l.onQueueUpdated(queue));
    }

    /**
     * Resumes playback if paused, replays current track if stopped, or starts queue if available.
     */
    public void play() {
        if (audioPlayer.getState() == PlaybackState.PAUSED) {
            audioPlayer.resume();
        } else if (audioPlayer.getCurrentTrack() != null) {
            audioPlayer.play(audioPlayer.getCurrentTrack());
        } else if (queue.getCurrentTrack() != null) {
            audioPlayer.play(queue.getCurrentTrack());
        } else if (queue.hasNext()) {
            Track next = queue.next();
            if (next != null) {
                audioPlayer.play(next);
            }
        }
    }

    /**
     * Pauses current playback.
     */
    public void pause() {
        audioPlayer.pause();
    }

    /**
     * Toggles between playing and paused states.
     */
    public void togglePlayPause() {
        if (audioPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    /**
     * Stops playback and resets LCD progress.
     */
    public void stop() {
        audioPlayer.stop();
        if (controlBarPanel != null) {
            controlBarPanel.updatePlaybackState(PlaybackState.STOPPED);
            controlBarPanel.updateProgress(0, 0);
        }
    }

    /**
     * Seeks playback position to the target seconds.
     *
     * @param seconds target seconds
     */
    public void seekTo(long seconds) {
        audioPlayer.seekTo(seconds);
    }

    /**
     * Skips playback forward by the specified number of seconds.
     *
     * @param seconds seconds to advance
     */
    public void skipForward(long seconds) {
        long current = audioPlayer.getCurrentPositionSeconds();
        long total = audioPlayer.getTotalDurationSeconds();
        long target = total > 0 ? Math.min(total, current + seconds) : current + seconds;
        seekTo(target);
    }

    /**
     * Skips playback forward by default duration (5 seconds).
     */
    public void skipForward() {
        skipForward(DEFAULT_SKIP_SECONDS);
    }

    /**
     * Skips playback backward by the specified number of seconds.
     *
     * @param seconds seconds to rewind
     */
    public void skipBackward(long seconds) {
        long current = audioPlayer.getCurrentPositionSeconds();
        long target = Math.max(0, current - seconds);
        seekTo(target);
    }

    /**
     * Skips playback backward by default duration (5 seconds).
     */
    public void skipBackward() {
        skipBackward(DEFAULT_SKIP_SECONDS);
    }

    /**
     * Sets linear audio volume in range [0.0f, 1.0f] and synchronizes volume slider.
     *
     * @param volume volume between 0.0f and 1.0f
     */
    public void setVolume(float volume) {
        float clamped = Math.max(0.0f, Math.min(1.0f, volume));
        audioPlayer.setVolume(clamped);
        syncVolumeSliderToPlayer();
    }

    /**
     * Sets audio volume from integer percent [0, 100].
     *
     * @param percent volume percent
     */
    public void setVolumePercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        setVolume(clamped / 100.0f);
    }

    /**
     * Returns audio volume as integer percent [0, 100].
     *
     * @return volume percent
     */
    public int getVolumePercent() {
        return Math.round(audioPlayer.getVolume() * 100);
    }

    /**
     * Advances to the next track in the queue, or resets position if no handler is registered.
     */
    public void next() {
        if (onNextAction != null) {
            onNextAction.run();
        } else if (queue.hasNext()) {
            Track nextTrack = queue.next();
            if (nextTrack != null) {
                audioPlayer.play(nextTrack);
            }
        } else {
            seekTo(0);
        }
    }

    /**
     * Returns to previous track, or rewinds to beginning of current track if > 3s elapsed.
     */
    public void previous() {
        if (audioPlayer.getCurrentPositionSeconds() > 3) {
            seekTo(0);
        } else if (onPreviousAction != null) {
            onPreviousAction.run();
        } else if (queue.hasPrevious()) {
            Track prevTrack = queue.previous();
            if (prevTrack != null) {
                audioPlayer.play(prevTrack);
            }
        } else {
            seekTo(0);
        }
    }

    /**
     * Toggles shuffle mode between on and off.
     */
    public void toggleShuffle() {
        setShuffle(!queue.isShuffle());
    }

    /**
     * Enables or disables shuffle mode and updates UI and listeners.
     *
     * @param shuffle true to enable shuffle
     */
    public void setShuffle(boolean shuffle) {
        queue.setShuffle(shuffle);
        if (controlBarPanel != null) {
            controlBarPanel.updateShuffleState(shuffle);
        }
        notifyQueueListeners(l -> l.onShuffleChanged(shuffle));
    }

    /**
     * Returns whether shuffle mode is active.
     *
     * @return true if shuffle is enabled
     */
    public boolean isShuffle() {
        return queue.isShuffle();
    }

    /**
     * Cycles through repeat modes: OFF -> ALL -> ONE -> OFF.
     *
     * @return new RepeatMode
     */
    public RepeatMode cycleRepeatMode() {
        RepeatMode newMode = queue.cycleRepeatMode();
        if (controlBarPanel != null) {
            controlBarPanel.updateRepeatMode(newMode);
        }
        notifyQueueListeners(l -> l.onRepeatModeChanged(newMode));
        return newMode;
    }

    /**
     * Sets repeat mode and synchronizes UI and listeners.
     *
     * @param mode new repeat mode
     */
    public void setRepeatMode(RepeatMode mode) {
        queue.setRepeatMode(mode);
        if (controlBarPanel != null) {
            controlBarPanel.updateRepeatMode(mode);
        }
        notifyQueueListeners(l -> l.onRepeatModeChanged(mode));
    }

    /**
     * Returns active repeat mode.
     *
     * @return current RepeatMode
     */
    public RepeatMode getRepeatMode() {
        return queue.getRepeatMode();
    }

    /**
     * Enqueues a track to play immediately after the current song.
     *
     * @param track track to enqueue next
     */
    public void enqueueNext(Track track) {
        if (track != null) {
            queue.enqueueNext(track);
            notifyQueueListeners(l -> l.onQueueUpdated(queue));
        }
    }

    /**
     * Returns the active playback queue.
     *
     * @return PlayQueue
     */
    public PlayQueue getQueue() {
        return queue;
    }

    /**
     * Registers a queue change observer.
     *
     * @param listener listener to register
     */
    public void addQueueListener(QueueChangeListener listener) {
        if (listener != null && !queueListeners.contains(listener)) {
            queueListeners.add(listener);
        }
    }

    /**
     * Unregisters a queue change observer.
     *
     * @param listener listener to remove
     */
    public void removeQueueListener(QueueChangeListener listener) {
        if (listener != null) {
            queueListeners.remove(listener);
        }
    }

    private void notifyQueueListeners(Consumer<QueueChangeListener> action) {
        runOnEdt(() -> {
            for (QueueChangeListener listener : queueListeners) {
                try {
                    action.accept(listener);
                } catch (Throwable ignored) {}
            }
        });
    }

    private void syncVolumeSliderToPlayer() {
        if (controlBarPanel == null) {
            return;
        }
        int sliderVal = Math.round(audioPlayer.getVolume() * 100);
        if (controlBarPanel.getVolumeSlider().getValue() != sliderVal) {
            isUpdatingVolume = true;
            try {
                controlBarPanel.getVolumeSlider().setValue(sliderVal);
            } finally {
                isUpdatingVolume = false;
            }
        }
    }

    // --- Action Handlers for Queue/Playlist Coordination ---

    public void setOnNextAction(Runnable onNextAction) {
        this.onNextAction = onNextAction;
    }

    public Runnable getOnNextAction() {
        return onNextAction;
    }

    public void setOnPreviousAction(Runnable onPreviousAction) {
        this.onPreviousAction = onPreviousAction;
    }

    public Runnable getOnPreviousAction() {
        return onPreviousAction;
    }

    public void setErrorHandler(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public ControlBarPanel getControlBarPanel() {
        return controlBarPanel;
    }

    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void close() {
        dispose();
    }

    /**
     * Releases listeners from AudioPlayer and ControlBarPanel components to prevent memory leaks.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        audioPlayer.removePlayerListener(playerListener);

        if (controlBarPanel != null) {
            if (playPauseListener != null) {
                controlBarPanel.getPlayPauseButton().removeActionListener(playPauseListener);
            }
            if (prevListener != null) {
                controlBarPanel.getPrevButton().removeActionListener(prevListener);
            }
            if (nextListener != null) {
                controlBarPanel.getNextButton().removeActionListener(nextListener);
            }
            if (volumeChangeListener != null) {
                controlBarPanel.getVolumeSlider().removeChangeListener(volumeChangeListener);
            }
            controlBarPanel.setSeekListener(null);
            controlBarPanel.setShuffleToggleListener(null);
            controlBarPanel.setRepeatCycleListener(null);
        }
        queueListeners.clear();
    }
}
