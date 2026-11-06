package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VlcjAudioPlayerTest {

    private AudioPlayerComponent mockComponent;
    private AtomicBoolean componentReleased;
    private AtomicBoolean componentReleaseThrows;

    private VlcjAudioPlayer player;
    private Track flacTrack;
    private Track mp3Track;

    @BeforeEach
    void setUp() throws Exception {
        componentReleased = new AtomicBoolean(false);
        componentReleaseThrows = new AtomicBoolean(false);

        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);

        MediaPlayer mockMp = createMock(MediaPlayer.class, (proxy, method, args) -> null);

        Class<?> teClass = Class.forName("uk.co.caprica.vlcj.support.eventmanager.TaskExecutor");
        Object taskExecutor = unsafe.allocateInstance(teClass);

        Field executorField = teClass.getDeclaredField("executor");
        executorField.setAccessible(true);
        executorField.set(taskExecutor, java.util.concurrent.Executors.newSingleThreadExecutor());

        Field mpExecutor = MediaPlayer.class.getDeclaredField("executor");
        mpExecutor.setAccessible(true);
        mpExecutor.set(mockMp, taskExecutor);

        mockComponent = createMock(AudioPlayerComponent.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("onBeforeRelease".equals(name) || "onAfterRelease".equals(name)) {
                componentReleased.set(true);
                if (componentReleaseThrows.get()) {
                    throw new RuntimeException("Simulated native release error");
                }
            }
            return null;
        });

        Field mpField = AudioPlayerComponent.class.getDeclaredField("mediaPlayer");
        mpField.setAccessible(true);
        mpField.set(mockComponent, mockMp);

        player = new VlcjAudioPlayer(mockComponent, null, 0.7f);

        flacTrack = Track.builder()
                .id("track-flac-1")
                .title("Moonlight Sonata")
                .artist("Beethoven")
                .album("Piano Sonatas")
                .durationSeconds(360)
                .filePath("C:/music/sonata.flac")
                .build();

        mp3Track = Track.builder()
                .id("track-mp3-1")
                .title("Clair de Lune")
                .artist("Debussy")
                .album("Suite Bergamasque")
                .durationSeconds(300)
                .filePath("C:/music/clair.mp3")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (player != null) {
            player.close();
        }
    }

    // ==========================================
    // 1. Initial State & Properties
    // ==========================================

    @Test
    @DisplayName("VlcjAudioPlayer initializes with default properties, volume, seekable and STOPPED state")
    void testInitialStateAndProperties() {
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        assertThat(player.isPlaying()).isFalse();
        assertThat(player.getCurrentTrack()).isNull();
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
        assertThat(player.getTotalDurationSeconds()).isEqualTo(0);
        assertThat(player.getVolume()).isEqualTo(0.7f);
        assertThat(player.isSeekable()).isTrue();
        assertThat(player.getAudioPlayerComponent()).isSameAs(mockComponent);
        assertThat(player.getMediaPlayer()).isNotNull();
        assertThat(player.getMediaPlayerEventListener()).isNotNull();
    }

    @Test
    @DisplayName("play throws IllegalArgumentException when track is null")
    void testPlayNullTrackThrows() {
        assertThatThrownBy(() -> player.play(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Track cannot be null");
    }

    // ==========================================
    // 2. Playback Lifecycle & State Transitions
    // ==========================================

    @Test
    @DisplayName("play initiates playback setup and playing event transitions to PLAYING on EDT")
    void testPlayAndPlayingEvent() throws Exception {
        List<PlaybackState> states = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Track> startedTrackRef = new AtomicReference<>();
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(2);

        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onStateChanged(PlaybackState newState) {
                states.add(newState);
                onEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            }

            @Override
            public void onTrackStarted(Track track) {
                startedTrackRef.set(track);
                latch.countDown();
            }
        });

        player.play(flacTrack);
        assertThat(player.getCurrentTrack()).isEqualTo(flacTrack);
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);

        // Simulate native VLC engine firing playing event
        MediaPlayerEventAdapter adapter = player.getMediaPlayerEventListener();
        adapter.playing(null);

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(player.isPlaying()).isTrue();
        assertThat(states).contains(PlaybackState.PLAYING);
        assertThat(startedTrackRef.get()).isEqualTo(flacTrack);
        assertThat(onEdt.get()).isTrue();

        // Calling playing again for the same track does not dispatch onTrackStarted a second time
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicInteger extraStartCount = new AtomicInteger(0);
        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onTrackStarted(Track track) {
                extraStartCount.incrementAndGet();
            }
        });
        adapter.playing(null);
        waitForEdt();
        assertThat(extraStartCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("pause and resume transition between PLAYING and PAUSED")
    void testPauseAndResumeTransitions() throws Exception {
        player.play(mp3Track);
        player.getMediaPlayerEventListener().playing(null);
        waitForEdt();
        assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);

        List<PlaybackState> stateChanges = Collections.synchronizedList(new ArrayList<>());
        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onStateChanged(PlaybackState newState) {
                stateChanges.add(newState);
            }
        });

        // 1. Pause
        player.pause();
        waitForEdt();
        assertThat(player.getState()).isEqualTo(PlaybackState.PAUSED);
        assertThat(player.isPlaying()).isFalse();
        assertThat(stateChanges).contains(PlaybackState.PAUSED);

        // 2. Pause when already PAUSED is a safe no-op
        stateChanges.clear();
        player.pause();
        waitForEdt();
        assertThat(stateChanges).isEmpty();

        // 3. Resume
        player.resume();
        waitForEdt();
        assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(player.isPlaying()).isTrue();
        assertThat(stateChanges).contains(PlaybackState.PLAYING);

        // 4. Resume when already PLAYING is a safe no-op
        stateChanges.clear();
        player.resume();
        waitForEdt();
        assertThat(stateChanges).isEmpty();

        // 5. Native paused event adapter callback
        player.getMediaPlayerEventListener().paused(null);
        waitForEdt();
        assertThat(player.getState()).isEqualTo(PlaybackState.PAUSED);
    }

    @Test
    @DisplayName("stop resets position, transitions to STOPPED, and notifies listeners on EDT")
    void testStopLifecycle() throws Exception {
        player.play(flacTrack);
        player.getMediaPlayerEventListener().playing(null);
        player.seekTo(150);
        waitForEdt();
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(150);

        AtomicReference<PlaybackState> lastState = new AtomicReference<>();
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onStateChanged(PlaybackState newState) {
                lastState.set(newState);
                onEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            }
        });

        player.stop();

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        assertThat(player.isPlaying()).isFalse();
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
        assertThat(lastState.get()).isEqualTo(PlaybackState.STOPPED);
        assertThat(onEdt.get()).isTrue();

        // Native stopped event
        player.getMediaPlayerEventListener().stopped(null);
        waitForEdt();
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
    }

    @Test
    @DisplayName("finished event transitions to STOPPED and notifies onTrackFinished on EDT")
    void testFinishedEventHandling() throws Exception {
        player.play(flacTrack);
        player.getMediaPlayerEventListener().playing(null);
        player.seekTo(359);
        waitForEdt();

        AtomicReference<Track> finishedRef = new AtomicReference<>();
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onTrackFinished(Track track) {
                finishedRef.set(track);
                onEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            }
        });

        player.getMediaPlayerEventListener().finished(null);

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(finishedRef.get()).isEqualTo(flacTrack);
        assertThat(onEdt.get()).isTrue();
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("timeChanged event updates position and dispatches onProgressUpdate on EDT")
    void testTimeChangedProgressUpdates() throws Exception {
        player.play(flacTrack);

        AtomicLong reportedCurrent = new AtomicLong(-1);
        AtomicLong reportedTotal = new AtomicLong(-1);
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onProgressUpdate(long currentSeconds, long totalSeconds) {
                reportedCurrent.set(currentSeconds);
                reportedTotal.set(totalSeconds);
                onEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            }
        });

        // 65000 ms = 65 seconds
        player.getMediaPlayerEventListener().timeChanged(null, 65000L);

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(reportedCurrent.get()).isEqualTo(65L);
        assertThat(reportedTotal.get()).isEqualTo(360L);
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(65L);
        assertThat(onEdt.get()).isTrue();
    }

    @Test
    @DisplayName("error event resets position to 0, transitions to STOPPED, and dispatches onError on EDT")
    void testNativeErrorEventHandling() throws Exception {
        player.play(flacTrack);
        player.getMediaPlayerEventListener().playing(null);
        player.seekTo(50);
        waitForEdt();

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        player.addPlayerListener(new PlayerListener() {
            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                onEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            }
        });

        player.getMediaPlayerEventListener().error(null);

        boolean received = latch.await(2, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(errorRef.get()).isNotNull();
        assertThat(errorRef.get().getMessage()).contains("VLC playback error");
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
        assertThat(onEdt.get()).isTrue();
    }

    // ==========================================
    // 3. Seeking & Volume Control
    // ==========================================

    @Test
    @DisplayName("seekTo clamps negative values to 0 and stores target position")
    void testSeekTo() {
        player.seekTo(45);
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(45);

        // Negative value clamps to 0
        player.seekTo(-30);
        assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("setVolume clamps values strictly to [0.0f, 1.0f]")
    void testSetVolumeClamping() {
        // Normal
        player.setVolume(0.45f);
        assertThat(player.getVolume()).isEqualTo(0.45f);

        // Upper clamp
        player.setVolume(1.8f);
        assertThat(player.getVolume()).isEqualTo(1.0f);

        // Lower clamp
        player.setVolume(-0.5f);
        assertThat(player.getVolume()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("getTotalDurationSeconds returns track duration when native status is not available")
    void testTotalDurationReturnsTrackDuration() {
        assertThat(player.getTotalDurationSeconds()).isEqualTo(0);

        player.play(flacTrack);
        assertThat(player.getTotalDurationSeconds()).isEqualTo(360);

        player.play(mp3Track);
        assertThat(player.getTotalDurationSeconds()).isEqualTo(300);
    }

    // ==========================================
    // 4. Resource Cleanup & Close
    // ==========================================

    @Test
    @DisplayName("close stops player, clears listeners, releases AudioPlayerComponent, and forbids subsequent play")
    void testCloseLifecycle() {
        player.play(flacTrack);
        player.close();

        assertThat(componentReleased.get()).isTrue();
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        assertThat(player.getCurrentTrack()).isNull();

        // Multiple close calls are safe idempotent no-ops
        assertThatCode(() -> player.close()).doesNotThrowAnyException();

        // Subsequent play throws IllegalStateException
        assertThatThrownBy(() -> player.play(flacTrack))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AudioPlayer is closed");
    }

    @Test
    @DisplayName("close safely catches and logs exceptions thrown during AudioPlayerComponent.release()")
    void testCloseWithFailingComponentRelease() {
        componentReleaseThrows.set(true);
        assertThatCode(() -> player.close()).doesNotThrowAnyException();
        assertThat(componentReleased.get()).isTrue();
        assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
    }

    @Test
    @DisplayName("removePlayerListener stops delivering callbacks to unregistered listener")
    void testRemovePlayerListener() throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        PlayerListener listener = new PlayerListener() {
            @Override
            public void onStateChanged(PlaybackState newState) {
                count.incrementAndGet();
            }
        };

        player.addPlayerListener(listener);
        player.play(flacTrack);
        player.getMediaPlayerEventListener().playing(null);
        waitForEdt();

        int recorded = count.get();
        assertThat(recorded).isGreaterThan(0);

        player.removePlayerListener(listener);
        player.pause();
        waitForEdt();

        assertThat(count.get()).isEqualTo(recorded);
    }

    // ==========================================
    // 5. Native Mock Exception Resilience Tests
    // ==========================================

    @Test
    @DisplayName("Native mock methods throwing exceptions are safely handled without crashing engine")
    void testNativeMockExceptionResilience() throws Exception {
        AtomicBoolean eventsCalled = new AtomicBoolean(false);
        AtomicBoolean removeListenerCalled = new AtomicBoolean(false);

        // ByteBuddy mock of MediaPlayer that throws RuntimeException on all API getters
        MediaPlayer failingMp = createMock(MediaPlayer.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("events".equals(name)) {
                eventsCalled.set(true);
                // Return an EventApi that throws or null
                throw new RuntimeException("Simulated native events() exception");
            } else if ("audio".equals(name)) {
                throw new RuntimeException("Simulated native audio() exception");
            } else if ("controls".equals(name)) {
                throw new RuntimeException("Simulated native controls() exception");
            } else if ("media".equals(name)) {
                throw new RuntimeException("Simulated native media() exception");
            } else if ("status".equals(name)) {
                throw new RuntimeException("Simulated native status() exception");
            }
            return null;
        });

        // Constructor catches exception during setVolume / events safely
        VlcjAudioPlayer resilientPlayer = new VlcjAudioPlayer(null, failingMp, 0.5f);

        try {
            assertThat(resilientPlayer.getVolume()).isEqualTo(0.5f);
            assertThat(resilientPlayer.getState()).isEqualTo(PlaybackState.STOPPED);

            // play() catches exception, transitions to STOPPED, and reports onError
            AtomicReference<Throwable> reportedError = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            resilientPlayer.addPlayerListener(new PlayerListener() {
                @Override
                public void onError(Throwable error) {
                    reportedError.set(error);
                    latch.countDown();
                }
            });

            resilientPlayer.play(flacTrack);
            boolean received = latch.await(2, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(reportedError.get()).isInstanceOf(RuntimeException.class);
            assertThat(resilientPlayer.getState()).isEqualTo(PlaybackState.STOPPED);

            // pause, resume, stop, seekTo, setVolume, getTotalDurationSeconds do not crash
            assertThatCode(() -> {
                resilientPlayer.pause();
                resilientPlayer.resume();
                resilientPlayer.stop();
                resilientPlayer.seekTo(10);
                resilientPlayer.setVolume(0.8f);
                long duration = resilientPlayer.getTotalDurationSeconds();
                assertThat(duration).isEqualTo(360L);
            }).doesNotThrowAnyException();
        } finally {
            resilientPlayer.close();
        }
    }

    // ==========================================
    // Helper: Wait for Swing EDT queue
    // ==========================================

    private static void waitForEdt() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(latch::countDown);
        boolean done = latch.await(2, TimeUnit.SECONDS);
        assertThat(done).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static <T> T createMock(Class<T> clazz, InvocationHandler handler) throws Exception {
        Class<? extends T> dynamicType = new ByteBuddy()
                .subclass(clazz, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(handler))
                .make()
                .load(VlcjAudioPlayerTest.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();

        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        return (T) unsafe.allocateInstance(dynamicType);
    }
}
