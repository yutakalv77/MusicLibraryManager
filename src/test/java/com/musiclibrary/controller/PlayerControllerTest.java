package com.musiclibrary.controller;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.ui.panel.ControlBarPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PlayerControllerTest {

    private DummyAudioPlayer dummyAudioPlayer;
    private ControlBarPanel controlBarPanel;
    private PlayerController controller;

    @BeforeEach
    void setUp() {
        com.musiclibrary.i18n.I18nManager.getInstance().setLocale(com.musiclibrary.i18n.AppLocale.ENGLISH);
        com.musiclibrary.theme.ThemeManager.getInstance().applyTheme(com.musiclibrary.theme.AppTheme.DARK);
        dummyAudioPlayer = new DummyAudioPlayer();
        controlBarPanel = new ControlBarPanel();
        controller = new PlayerController(dummyAudioPlayer, controlBarPanel);
    }

    @Test
    @DisplayName("PlayerController binds ControlBarPanel and AudioPlayer with synchronized volume and initial state")
    void testInitializationAndBinding() {
        assertThat(controller.getAudioPlayer()).isSameAs(dummyAudioPlayer);
        assertThat(controller.getControlBarPanel()).isSameAs(controlBarPanel);

        // Initial volume sync
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(0.7f);
        assertThat(controlBarPanel.getVolumeSlider().getValue()).isEqualTo(70);
    }

    @Test
    @DisplayName("play(track) and play() delegate appropriately based on playback state")
    void testPlayOperations() {
        Track track = Track.builder().id("1").title("Song 1").filePath("song1.mp3").build();

        // 1. play(track)
        controller.play(track);
        assertThat(dummyAudioPlayer.lastPlayedTrack).isSameAs(track);
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.PLAYING);

        // 2. pause()
        controller.pause();
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.PAUSED);

        // 3. play() resumes paused playback
        controller.play();
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.PLAYING);

        // 4. play(null) is safe no-op
        controller.play((Track) null);
        assertThat(dummyAudioPlayer.lastPlayedTrack).isSameAs(track);
    }

    @Test
    @DisplayName("togglePlayPause toggles between PLAYING and PAUSED states")
    void testTogglePlayPause() {
        Track track = Track.builder().id("1").title("Song 1").filePath("song1.mp3").build();
        controller.play(track);
        assertThat(dummyAudioPlayer.isPlaying()).isTrue();

        // Toggle while playing -> pauses
        controller.togglePlayPause();
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.PAUSED);

        // Toggle while paused -> plays/resumes
        controller.togglePlayPause();
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.PLAYING);
    }

    @Test
    @DisplayName("stop halts playback and resets ControlBarPanel state and progress")
    void testStop() {
        Track track = Track.builder().id("1").title("Song 1").filePath("song1.mp3").build();
        controller.play(track);
        controller.stop();

        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.STOPPED);
        assertThat(controlBarPanel.getSeekSlider().getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("seekTo, skipForward, and skipBackward update player position accurately")
    void testSeekingAndSkipping() {
        dummyAudioPlayer.totalDurationSeconds = 120;
        dummyAudioPlayer.currentPositionSeconds = 30;

        // 1. Direct seekTo
        controller.seekTo(60);
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(60);

        // 2. skipForward default (5s)
        controller.skipForward();
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(65);

        // 3. skipForward with explicit seconds
        controller.skipForward(10);
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(75);

        // 4. skipForward clamped to total duration
        controller.skipForward(100);
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(120);

        // 5. skipBackward default (5s)
        controller.skipBackward();
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(115);

        // 6. skipBackward with explicit seconds
        controller.skipBackward(15);
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(100);

        // 7. skipBackward clamped to 0
        controller.skipBackward(200);
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(0);
    }

    @Test
    @DisplayName("setVolume and setVolumePercent clamp values and synchronize volume slider")
    void testVolumeControl() {
        // Float volume
        controller.setVolume(0.5f);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(0.5f);
        assertThat(controlBarPanel.getVolumeSlider().getValue()).isEqualTo(50);

        // Percent volume
        controller.setVolumePercent(85);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(0.85f);
        assertThat(controlBarPanel.getVolumeSlider().getValue()).isEqualTo(85);

        // Clamping upper & lower bounds
        controller.setVolume(-0.2f);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(0.0f);
        assertThat(controlBarPanel.getVolumeSlider().getValue()).isEqualTo(0);

        controller.setVolume(1.5f);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(1.0f);
        assertThat(controlBarPanel.getVolumeSlider().getValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("next and previous invoke custom hooks or default to seeking to start")
    void testNextAndPrevious() {
        AtomicBoolean nextFired = new AtomicBoolean(false);
        AtomicBoolean prevFired = new AtomicBoolean(false);

        controller.setOnNextAction(() -> nextFired.set(true));
        controller.setOnPreviousAction(() -> prevFired.set(true));

        // 1. next() triggers registered hook
        controller.next();
        assertThat(nextFired.get()).isTrue();

        // 2. previous() with <= 3s triggers registered hook
        dummyAudioPlayer.currentPositionSeconds = 2;
        controller.previous();
        assertThat(prevFired.get()).isTrue();

        // 3. previous() with > 3s rewinds to beginning of current track without calling onPreviousAction
        prevFired.set(false);
        dummyAudioPlayer.currentPositionSeconds = 10;
        controller.previous();
        assertThat(prevFired.get()).isFalse();
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(0);

        // 4. Default next/prev without hooks
        controller.setOnNextAction(null);
        controller.setOnPreviousAction(null);
        dummyAudioPlayer.currentPositionSeconds = 40;
        controller.next();
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(0);
    }

    @Test
    @DisplayName("Engine events propagate seamlessly to ControlBarPanel LCD and error handler")
    void testAudioPlayerListenerPropagation() throws Exception {
        Track track = Track.builder()
                .id("t1")
                .title("Echoes")
                .artist("Pink Floyd")
                .album("Meddle")
                .durationSeconds(1400)
                .filePath("echoes.mp3")
                .build();

        // Track started
        dummyAudioPlayer.fireTrackStarted(track);
        // State changed
        dummyAudioPlayer.fireStateChanged(PlaybackState.PLAYING);
        flushEdt();
        assertThat(controlBarPanel.getPlayPauseButton().getText()).isEqualTo("⏸");

        // Progress update
        dummyAudioPlayer.fireProgressUpdate(700, 1400);
        flushEdt();
        assertThat(controlBarPanel.getCurrentTimeLabel().getText()).isEqualTo("11:40");
        assertThat(controlBarPanel.getTotalTimeLabel().getText()).isEqualTo("23:20");
        assertThat(controlBarPanel.getSeekSlider().getValue()).isEqualTo(500);

        // Track finished triggers next()
        AtomicBoolean nextTriggered = new AtomicBoolean(false);
        controller.setOnNextAction(() -> nextTriggered.set(true));
        dummyAudioPlayer.fireTrackFinished(track);
        flushEdt();
        assertThat(nextTriggered.get()).isTrue();

        // Error callback
        AtomicReference<Throwable> reportedError = new AtomicReference<>();
        controller.setErrorHandler(reportedError::set);
        RuntimeException ex = new RuntimeException("Decoder failure");
        dummyAudioPlayer.fireError(ex);
        flushEdt();
        assertThat(reportedError.get()).isSameAs(ex);
    }

    private void flushEdt() throws Exception {
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});
        }
    }

    @Test
    @DisplayName("PlayerController functions safely with null ControlBarPanel")
    void testNullControlBarPanelSafe() {
        PlayerController headlessController = new PlayerController(dummyAudioPlayer, null);
        Track track = Track.builder().id("h1").title("Headless").filePath("h1.mp3").build();

        assertThatCode(() -> {
            headlessController.play(track);
            headlessController.pause();
            headlessController.play();
            headlessController.togglePlayPause();
            headlessController.seekTo(10);
            headlessController.skipForward();
            headlessController.skipBackward();
            headlessController.setVolume(0.5f);
            headlessController.setVolumePercent(80);
            headlessController.stop();
            headlessController.next();
            headlessController.previous();
            headlessController.dispose();
            headlessController.close();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("play() replays currentTrack when stopped, or does nothing when currentTrack is null")
    void testPlayStoppedWithCurrentTrackAndNullTrack() {
        Track track = Track.builder().id("1").title("Track 1").filePath("1.mp3").build();
        dummyAudioPlayer.currentTrack = track;
        dummyAudioPlayer.state = PlaybackState.STOPPED;

        // Stopped with currentTrack -> re-plays currentTrack
        controller.play();
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.PLAYING);
        assertThat(dummyAudioPlayer.lastPlayedTrack).isSameAs(track);

        // Stopped with null currentTrack -> safe no-op
        dummyAudioPlayer.stop();
        dummyAudioPlayer.currentTrack = null;
        controller.play();
        assertThat(dummyAudioPlayer.state).isEqualTo(PlaybackState.STOPPED);
    }

    @Test
    @DisplayName("Directly adjusting volume slider synchronizes volume to AudioPlayer without recursion loop")
    void testVolumeSliderChangeSynchronizesToPlayer() {
        // Change volume slider from UI
        controlBarPanel.getVolumeSlider().setValue(35);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(0.35f);

        controlBarPanel.getVolumeSlider().setValue(0);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(0.0f);

        controlBarPanel.getVolumeSlider().setValue(100);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("skipForward advances unbounded when total duration is zero or unknown")
    void testSkipBoundariesWithZeroTotalDuration() {
        dummyAudioPlayer.totalDurationSeconds = 0;
        dummyAudioPlayer.currentPositionSeconds = 15;

        controller.skipForward(10);
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(25);

        controller.skipForward(); // default 5s
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(30);
    }

    @Test
    @DisplayName("Seek slider commit in ControlBarPanel dispatches seekTo to AudioPlayer via seekListener")
    void testSeekSliderCommitTriggersSeekTo() {
        dummyAudioPlayer.totalDurationSeconds = 200;
        controlBarPanel.updateProgress(10, 200);

        // Simulate user committing seek to 50% (value 500 of 1000)
        controlBarPanel.getSeekSlider().setValue(500);

        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(100);
    }

    @Test
    @DisplayName("previous() respects 3-second boundary precisely (<= 3s triggers prev hook, > 3s rewinds to 0)")
    void testPreviousBoundariesComprehensive() {
        AtomicBoolean prevFired = new AtomicBoolean(false);
        controller.setOnPreviousAction(() -> prevFired.set(true));

        // 1. Boundary: exactly 3 seconds elapsed (<= 3s) -> calls onPreviousAction
        dummyAudioPlayer.currentPositionSeconds = 3;
        prevFired.set(false);
        controller.previous();
        assertThat(prevFired.get()).isTrue();

        // 2. Boundary: exactly 4 seconds elapsed (> 3s) -> rewinds to 0, does NOT call onPreviousAction
        dummyAudioPlayer.currentPositionSeconds = 4;
        prevFired.set(false);
        controller.previous();
        assertThat(prevFired.get()).isFalse();
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(0);

        // 3. Boundary: 0 seconds elapsed (<= 3s) -> calls onPreviousAction
        dummyAudioPlayer.currentPositionSeconds = 0;
        prevFired.set(false);
        controller.previous();
        assertThat(prevFired.get()).isTrue();

        // 4. Boundary: <= 3s but onPreviousAction is null -> safely falls back to seekTo(0)
        controller.setOnPreviousAction(null);
        dummyAudioPlayer.currentPositionSeconds = 2;
        controller.previous();
        assertThat(dummyAudioPlayer.currentPositionSeconds).isEqualTo(0);
    }

    @Test
    @DisplayName("AudioPlayer events handle null track safely and unhandled errors without uncaught exceptions")
    void testAudioPlayerListenerSafety() throws Exception {
        // null track in onTrackStarted
        assertThatCode(() -> dummyAudioPlayer.fireTrackStarted(null)).doesNotThrowAnyException();
        flushEdt();

        // onError when errorHandler is null
        controller.setErrorHandler(null);
        assertThatCode(() -> dummyAudioPlayer.fireError(new IllegalStateException("Engine issue"))).doesNotThrowAnyException();
        flushEdt();
    }

    @Test
    @DisplayName("dispose unbinds UI buttons and volume slider, preventing subsequent actions")
    void testDisposeUnbindsAllUiListenersAndIdempotence() {
        Track track = Track.builder().id("1").title("Song").filePath("song.mp3").build();
        dummyAudioPlayer.play(track);
        assertThat(dummyAudioPlayer.isPlaying()).isTrue();

        // Dispose controller
        controller.dispose();

        // 1. Play/Pause button click after dispose should NOT affect dummyAudioPlayer
        controlBarPanel.getPlayPauseButton().doClick();
        assertThat(dummyAudioPlayer.isPlaying()).isTrue(); // still playing, listener removed!

        // 2. Volume slider change after dispose should NOT update dummyAudioPlayer volume
        float initialVolume = dummyAudioPlayer.getVolume();
        controlBarPanel.getVolumeSlider().setValue(10);
        assertThat(dummyAudioPlayer.getVolume()).isEqualTo(initialVolume);

        // 3. Multiple dispose / close calls are safe and idempotent
        assertThatCode(() -> {
            controller.dispose();
            controller.close();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("play(track, contextTracks) populates queue and plays target track")
    void testPlayWithContextTracksInitializesQueue() {
        Track t1 = Track.builder().id("1").title("Song 1").filePath("s1.mp3").build();
        Track t2 = Track.builder().id("2").title("Song 2").filePath("s2.mp3").build();
        Track t3 = Track.builder().id("3").title("Song 3").filePath("s3.mp3").build();

        controller.play(t2, List.of(t1, t2, t3));

        assertThat(controller.getQueue().getCurrentTrack()).isEqualTo(t2);
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t2);
        assertThat(dummyAudioPlayer.isPlaying()).isTrue();
    }

    @Test
    @DisplayName("Track finish automatically advances to next track in queue until queue ends")
    void testAutoContinuousPlaybackOnTrackFinished() throws Exception {
        Track t1 = Track.builder().id("1").title("Song 1").filePath("s1.mp3").build();
        Track t2 = Track.builder().id("2").title("Song 2").filePath("s2.mp3").build();

        controller.play(t1, List.of(t1, t2));
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t1);

        // Track 1 finishes -> automatically plays track 2
        dummyAudioPlayer.fireTrackFinished(t1);
        flushEdt();

        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t2);
        assertThat(dummyAudioPlayer.isPlaying()).isTrue();

        // Track 2 finishes -> queue ends -> player stops
        dummyAudioPlayer.fireTrackFinished(t2);
        flushEdt();

        assertThat(dummyAudioPlayer.getState()).isEqualTo(PlaybackState.STOPPED);
    }

    @Test
    @DisplayName("next and previous navigate queue and handle >3s rewind logic")
    void testNextAndPreviousNavigation() {
        Track t1 = Track.builder().id("1").title("Song 1").filePath("s1.mp3").build();
        Track t2 = Track.builder().id("2").title("Song 2").filePath("s2.mp3").build();
        Track t3 = Track.builder().id("3").title("Song 3").filePath("s3.mp3").build();

        controller.play(t1, List.of(t1, t2, t3));

        // next() advances to t2
        controller.next();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t2);

        // previous() with position > 3s rewinds to 0 without changing track
        dummyAudioPlayer.seekTo(10);
        controller.previous();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t2);
        assertThat(dummyAudioPlayer.getCurrentPositionSeconds()).isEqualTo(0);

        // previous() with position <= 3s returns to t1
        dummyAudioPlayer.seekTo(2);
        controller.previous();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t1);
    }

    @Test
    @DisplayName("Shuffle and repeat mode cycle and notify listeners and control bar panel")
    void testShuffleAndRepeatModeTogglingAndListeners() throws Exception {
        AtomicReference<RepeatMode> observedRepeat = new AtomicReference<>();
        AtomicBoolean observedShuffle = new AtomicBoolean(false);

        controller.addQueueListener(new QueueChangeListener() {
            @Override
            public void onRepeatModeChanged(RepeatMode mode) {
                observedRepeat.set(mode);
            }

            @Override
            public void onShuffleChanged(boolean shuffle) {
                observedShuffle.set(shuffle);
            }
        });

        // Repeat mode cycling
        assertThat(controller.getRepeatMode()).isEqualTo(RepeatMode.OFF);

        controller.cycleRepeatMode();
        flushEdt();
        assertThat(controller.getRepeatMode()).isEqualTo(RepeatMode.ALL);
        assertThat(observedRepeat.get()).isEqualTo(RepeatMode.ALL);

        controller.cycleRepeatMode();
        flushEdt();
        assertThat(controller.getRepeatMode()).isEqualTo(RepeatMode.ONE);
        assertThat(observedRepeat.get()).isEqualTo(RepeatMode.ONE);

        controller.cycleRepeatMode();
        flushEdt();
        assertThat(controller.getRepeatMode()).isEqualTo(RepeatMode.OFF);
        assertThat(observedRepeat.get()).isEqualTo(RepeatMode.OFF);

        // Shuffle toggling
        assertThat(controller.isShuffle()).isFalse();

        controller.toggleShuffle();
        flushEdt();
        assertThat(controller.isShuffle()).isTrue();
        assertThat(observedShuffle.get()).isTrue();

        controller.toggleShuffle();
        flushEdt();
        assertThat(controller.isShuffle()).isFalse();
        assertThat(observedShuffle.get()).isFalse();
    }

    @Test
    @DisplayName("enqueueNext inserts track into priority queue ahead of regular playlist order")
    void testEnqueueNextPriorityQueue() {
        Track t1 = Track.builder().id("1").title("Song 1").filePath("s1.mp3").build();
        Track t2 = Track.builder().id("2").title("Song 2").filePath("s2.mp3").build();
        Track priorityTrack = Track.builder().id("p").title("Priority Song").filePath("p.mp3").build();

        controller.play(t1, List.of(t1, t2));

        // Enqueue priority track
        controller.enqueueNext(priorityTrack);

        // Next should play priorityTrack instead of regular next track (t2)
        controller.next();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(priorityTrack);

        // Next should resume regular playlist order (t2)
        controller.next();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t2);
    }

    @Test
    @DisplayName("onTrackFinished handles RepeatMode.ALL, RepeatMode.ONE, and priority queue interruptions seamlessly")
    void testAutoContinuousPlaybackWithRepeatModesAndPriority() throws Exception {
        Track t1 = Track.builder().id("1").title("Song 1").filePath("s1.mp3").build();
        Track t2 = Track.builder().id("2").title("Song 2").filePath("s2.mp3").build();
        Track pri = Track.builder().id("p").title("Priority").filePath("p.mp3").build();

        controller.play(t1, List.of(t1, t2));

        // 1. Priority queue interruption via onTrackFinished
        controller.enqueueNext(pri);
        dummyAudioPlayer.fireTrackFinished(t1);
        flushEdt();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(pri);

        // Priority finishes -> resumes t2
        dummyAudioPlayer.fireTrackFinished(pri);
        flushEdt();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t2);

        // 2. RepeatMode.ALL: t2 finishes -> loops back to t1
        controller.setRepeatMode(RepeatMode.ALL);
        dummyAudioPlayer.fireTrackFinished(t2);
        flushEdt();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t1);

        // 3. RepeatMode.ONE: t1 finishes -> replays t1
        controller.setRepeatMode(RepeatMode.ONE);
        dummyAudioPlayer.fireTrackFinished(t1);
        flushEdt();
        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t1);
    }

    @Test
    @DisplayName("removeQueueListener cleanly unregisters listener")
    void testRemoveQueueListener() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        QueueChangeListener listener = new QueueChangeListener() {
            @Override
            public void onShuffleChanged(boolean shuffle) {
                called.set(true);
            }
        };

        controller.addQueueListener(listener);
        controller.removeQueueListener(listener);

        controller.toggleShuffle();
        flushEdt();

        assertThat(called.get()).isFalse();
    }

    @Test
    @DisplayName("play() resumes from queue when AudioPlayer is stopped and has no currentTrack")
    void testPlayStartsFromQueueWhenPlayerStopped() {
        Track t1 = Track.builder().id("1").title("Song 1").filePath("s1.mp3").build();
        controller.getQueue().setTracks(List.of(t1), 0);

        dummyAudioPlayer.stop();
        dummyAudioPlayer.currentTrack = null;

        controller.play();

        assertThat(dummyAudioPlayer.getCurrentTrack()).isEqualTo(t1);
        assertThat(dummyAudioPlayer.isPlaying()).isTrue();
    }

    // --- Mock / Dummy AudioPlayer Implementation ---

    private static class DummyAudioPlayer implements AudioPlayer {
        private final List<PlayerListener> listeners = new ArrayList<>();
        private PlaybackState state = PlaybackState.STOPPED;
        private Track currentTrack = null;
        private Track lastPlayedTrack = null;
        private float volume = 0.7f;
        private long currentPositionSeconds = 0;
        private long totalDurationSeconds = 0;

        @Override
        public void play(Track track) {
            this.currentTrack = track;
            this.lastPlayedTrack = track;
            this.state = PlaybackState.PLAYING;
            fireStateChanged(PlaybackState.PLAYING);
            fireTrackStarted(track);
        }

        @Override
        public void pause() {
            if (state == PlaybackState.PLAYING) {
                this.state = PlaybackState.PAUSED;
                fireStateChanged(PlaybackState.PAUSED);
            }
        }

        @Override
        public void resume() {
            if (state == PlaybackState.PAUSED) {
                this.state = PlaybackState.PLAYING;
                fireStateChanged(PlaybackState.PLAYING);
            }
        }

        @Override
        public void stop() {
            this.state = PlaybackState.STOPPED;
            this.currentPositionSeconds = 0;
            fireStateChanged(PlaybackState.STOPPED);
        }

        @Override
        public void seekTo(long seconds) {
            this.currentPositionSeconds = Math.max(0, seconds);
            fireProgressUpdate(this.currentPositionSeconds, this.totalDurationSeconds);
        }

        @Override
        public void setVolume(float volume) {
            this.volume = volume;
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
            return totalDurationSeconds;
        }

        @Override
        public boolean isPlaying() {
            return state == PlaybackState.PLAYING;
        }

        @Override
        public void addPlayerListener(PlayerListener listener) {
            if (listener != null) listeners.add(listener);
        }

        @Override
        public void removePlayerListener(PlayerListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void close() {
            stop();
            listeners.clear();
        }

        void fireStateChanged(PlaybackState state) {
            for (PlayerListener l : new ArrayList<>(listeners)) l.onStateChanged(state);
        }

        void fireTrackStarted(Track track) {
            for (PlayerListener l : new ArrayList<>(listeners)) l.onTrackStarted(track);
        }

        void fireProgressUpdate(long cur, long total) {
            for (PlayerListener l : new ArrayList<>(listeners)) l.onProgressUpdate(cur, total);
        }

        void fireTrackFinished(Track track) {
            for (PlayerListener l : new ArrayList<>(listeners)) l.onTrackFinished(track);
        }

        void fireError(Throwable error) {
            for (PlayerListener l : new ArrayList<>(listeners)) l.onError(error);
        }
    }
}
