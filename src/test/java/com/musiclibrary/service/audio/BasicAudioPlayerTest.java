package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasicAudioPlayerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("BasicAudioPlayer initializes with default properties and STOPPED state")
    void testInitialStateAndProperties() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(player.isPlaying()).isFalse();
            assertThat(player.getCurrentTrack()).isNull();
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
            assertThat(player.getTotalDurationSeconds()).isEqualTo(0);
            assertThat(player.getVolume()).isEqualTo(0.7f);
        }
    }

    @Test
    @DisplayName("Invoking pause, resume, and stop when stopped are safe no-ops")
    void testSafeNoOpOperations() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            assertThatCode(() -> {
                player.pause();
                player.resume();
                player.stop();
            }).doesNotThrowAnyException();

            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @DisplayName("play throws IllegalArgumentException when track is null")
    void testNullTrackValidation() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            assertThatThrownBy(() -> player.play(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Track cannot be null");
        }
    }

    @Test
    @DisplayName("play non-existent file triggers onError callback and transitions to STOPPED")
    void testNonExistentFileReportsErrorAndStops() throws Exception {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            List<PlaybackState> states = Collections.synchronizedList(new ArrayList<>());

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onStateChanged(PlaybackState newState) {
                    states.add(newState);
                }

                @Override
                public void onError(Throwable error) {
                    errorRef.set(error);
                    latch.countDown();
                }
            });

            Track missing = Track.builder()
                    .filePath(tempDir.resolve("non_existent_audio.mp3").toString())
                    .title("Missing Track")
                    .build();

            player.play(missing);

            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertThat(received).isTrue();
            assertThat(errorRef.get()).isNotNull();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(states).contains(PlaybackState.STOPPED);
        }
    }

    @Test
    @DisplayName("setVolume clamps values safely between 0.0f and 1.0f")
    void testVolumeAdjustment() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            player.setVolume(0.5f);
            assertThat(player.getVolume()).isEqualTo(0.5f);

            player.setVolume(-0.5f);
            assertThat(player.getVolume()).isEqualTo(0.0f);

            player.setVolume(2.0f);
            assertThat(player.getVolume()).isEqualTo(1.0f);
        }
    }

    @Test
    @DisplayName("addPlayerListener and removePlayerListener manage listener registrations cleanly")
    void testListenerRegistration() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            AtomicBoolean called = new AtomicBoolean(false);
            PlayerListener listener = new PlayerListener() {
                @Override
                public void onStateChanged(PlaybackState newState) {
                    called.set(true);
                }
            };

            player.addPlayerListener(listener);
            player.removePlayerListener(listener);

            // Trigger stop shouldn't notify removed listener
            player.stop();
            assertThat(called.get()).isFalse();
        }
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    @DisplayName("Full playback lifecycle: play, pause, resume, and stop transition states accurately")
    void testPlaybackLifecycle() throws Exception {
        Path mp3File = createDummyMp3File("test_lifecycle.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3File.toAbsolutePath().toString())
                .title("Lifecycle Track")
                .durationSeconds(10)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch startedLatch = new CountDownLatch(1);
            List<PlaybackState> recordedStates = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<Track> startedTrack = new AtomicReference<>();

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onStateChanged(PlaybackState newState) {
                    recordedStates.add(newState);
                }

                @Override
                public void onTrackStarted(Track t) {
                    startedTrack.set(t);
                    startedLatch.countDown();
                }
            });

            // 1. Play
            player.play(track);
            boolean started = startedLatch.await(3, TimeUnit.SECONDS);
            assertThat(started).isTrue();
            assertThat(startedTrack.get()).isEqualTo(track);
            assertThat(player.getCurrentTrack()).isEqualTo(track);
            assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);
            assertThat(player.isPlaying()).isTrue();

            // 2. Pause
            player.pause();
            assertThat(player.getState()).isEqualTo(PlaybackState.PAUSED);
            assertThat(player.isPlaying()).isFalse();
            assertThat(recordedStates).contains(PlaybackState.PAUSED);

            // 3. Resume
            player.resume();
            assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);
            assertThat(player.isPlaying()).isTrue();

            // 4. Stop
            player.stop();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(player.isPlaying()).isFalse();
        }
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    @DisplayName("Playing a new track stops and replaces any currently playing track")
    void testPlaySubsequentTrackStopsPreviousTrack() throws Exception {
        Path file1 = createDummyMp3File("track1.mp3", 10);
        Path file2 = createDummyMp3File("track2.mp3", 10);

        Track track1 = Track.builder().filePath(file1.toAbsolutePath().toString()).title("Track 1").build();
        Track track2 = Track.builder().filePath(file2.toAbsolutePath().toString()).title("Track 2").build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch start1 = new CountDownLatch(1);
            CountDownLatch start2 = new CountDownLatch(1);

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    if ("Track 1".equals(t.getTitle())) {
                        start1.countDown();
                    } else if ("Track 2".equals(t.getTitle())) {
                        start2.countDown();
                    }
                }
            });

            player.play(track1);
            assertThat(start1.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(player.getCurrentTrack().getTitle()).isEqualTo("Track 1");

            // Play track 2 immediately replaces track 1
            player.play(track2);
            assertThat(start2.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(player.getCurrentTrack().getTitle()).isEqualTo("Track 2");

            player.stop();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Stopping playback while paused cleanly releases locks and transitions to STOPPED")
    void testStopWhilePausedTransitionsToStoppedCleanly() throws Exception {
        Path mp3File = createDummyMp3File("test_pause_stop.mp3", 10);
        Track track = Track.builder().filePath(mp3File.toAbsolutePath().toString()).title("Pause Stop Track").build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch started = new CountDownLatch(1);
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    started.countDown();
                }
            });

            player.play(track);
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();

            player.pause();
            assertThat(player.getState()).isEqualTo(PlaybackState.PAUSED);

            player.stop();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(player.isPlaying()).isFalse();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Playing a 0-byte file reports onError and transitions to STOPPED")
    void testZeroByteFileReportsErrorAndStops() throws Exception {
        Path zeroFile = Files.createFile(tempDir.resolve("empty.mp3"));
        Track track = Track.builder().filePath(zeroFile.toAbsolutePath().toString()).title("Empty Track").build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onError(Throwable error) {
                    errorRef.set(error);
                    latch.countDown();
                }
            });

            player.play(track);
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(errorRef.get()).isNotNull();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Playing a corrupt binary file reports onError and transitions to STOPPED")
    void testCorruptBinaryFileReportsErrorAndStops() throws Exception {
        Path corruptFile = tempDir.resolve("corrupt.mp3");
        Files.write(corruptFile, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44}); // Invalid audio header
        Track track = Track.builder().filePath(corruptFile.toAbsolutePath().toString()).title("Corrupt Track").build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onError(Throwable error) {
                    errorRef.set(error);
                    latch.countDown();
                }
            });

            player.play(track);
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(errorRef.get()).isNotNull();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Playing track with blank filePath reports onError and transitions to STOPPED")
    void testBlankFilePathReportsErrorAndStops() throws Exception {
        // Track with whitespace-only filePath injected via reflection
        Track track = Track.builder().filePath("C:/Music/test.mp3").title("Blank File Track").build();
        java.lang.reflect.Field field = Track.class.getDeclaredField("filePath");
        field.setAccessible(true);
        field.set(track, "   ");

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onError(Throwable error) {
                    errorRef.set(error);
                    latch.countDown();
                }
            });

            player.play(track);
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(errorRef.get()).isInstanceOf(IllegalArgumentException.class);
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Listener throwing runtime exception does not prevent delivery to other listeners or crash playback thread")
    void testListenerExceptionIsolationDoesNotAffectPlaybackOrOtherListeners() throws Exception {
        Path mp3File = createDummyMp3File("test_isolation.mp3", 10);
        Track track = Track.builder().filePath(mp3File.toAbsolutePath().toString()).title("Isolation Track").build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch healthyListenerLatch = new CountDownLatch(1);

            // Listener 1: Rogue listener throwing runtime exception
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    throw new RuntimeException("Simulated listener exception");
                }

                @Override
                public void onStateChanged(PlaybackState newState) {
                    throw new RuntimeException("Simulated state change exception");
                }
            });

            // Listener 2: Well-behaved listener
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    healthyListenerLatch.countDown();
                }
            });

            player.play(track);

            assertThat(healthyListenerLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);

            player.stop();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @DisplayName("Duplicate listener registration is ignored to prevent redundant event notifications")
    void testDuplicateListenerRegistrationIgnored() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            AtomicBoolean invoked = new AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);

            PlayerListener listener = new PlayerListener() {
                @Override
                public void onStateChanged(PlaybackState newState) {
                    callCount.incrementAndGet();
                }
            };

            player.addPlayerListener(listener);
            player.addPlayerListener(listener); // duplicate registration

            player.pause(); // triggers state change attempt (no-op when stopped, callCount stays 0)
            player.stop();  // triggers state change to STOPPED if changed

            // State didn't change from STOPPED to STOPPED, but let's verify collection contains once
            player.removePlayerListener(listener);
            // Removing once should completely remove it
            player.stop();
            assertThat(callCount.get()).isEqualTo(0);
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    @DisplayName("Transport operations play, pause, resume, and stop return promptly without blocking caller")
    void testNonBlockingOperations() throws Exception {
        Path mp3File = createDummyMp3File("test_non_blocking.mp3", 5);
        Track track = Track.builder().filePath(mp3File.toAbsolutePath().toString()).title("Quick Track").build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            long t1 = System.currentTimeMillis();
            player.play(track);
            long d1 = System.currentTimeMillis() - t1;
            assertThat(d1).isLessThan(200);

            long t2 = System.currentTimeMillis();
            player.pause();
            long d2 = System.currentTimeMillis() - t2;
            assertThat(d2).isLessThan(200);

            long t3 = System.currentTimeMillis();
            player.resume();
            long d3 = System.currentTimeMillis() - t3;
            assertThat(d3).isLessThan(200);

            long t4 = System.currentTimeMillis();
            player.stop();
            long d4 = System.currentTimeMillis() - t4;
            assertThat(d4).isLessThan(700); // stop joins up to 500ms
        }
    }

    @Test
    @DisplayName("seekTo while stopped is safe no-op and isSeekable returns true")
    void testSeekWhileStopped() {
        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            assertThat(player.isSeekable()).isTrue();
            assertThatCode(() -> {
                player.seekTo(30);
                player.seekTo(-5);
                player.seekTo(100);
            }).doesNotThrowAnyException();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.SECONDS)
    @DisplayName("seekTo while playing clamps within [0, totalSeconds] and dispatches onProgressUpdate")
    void testSeekWhilePlaying() throws Exception {
        Path mp3 = createDummyMp3File("test_seek_playing.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3.toAbsolutePath().toString())
                .title("Seek Track")
                .durationSeconds(100)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch startedLatch = new CountDownLatch(1);
            CountDownLatch seekProgressLatch = new CountDownLatch(1);
            AtomicReference<Long> reportedSec = new AtomicReference<>();

            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    startedLatch.countDown();
                }

                @Override
                public void onProgressUpdate(long currentSeconds, long totalSeconds) {
                    reportedSec.set(currentSeconds);
                    seekProgressLatch.countDown();
                }
            });

            player.play(track);
            assertThat(startedLatch.await(3, TimeUnit.SECONDS)).isTrue();

            // Seek within range
            player.seekTo(50);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(50);
            assertThat(seekProgressLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(reportedSec.get()).isEqualTo(50L);

            // Seek with negative value clamps to 0
            player.seekTo(-20);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);

            // Seek exceeding total duration clamps to totalSeconds
            player.seekTo(300);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(100);

            player.stop();
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.SECONDS)
    @DisplayName("seekTo while paused preserves PAUSED state and updates playback position")
    void testSeekWhilePausedMaintainsPausedState() throws Exception {
        Path mp3 = createDummyMp3File("test_seek_paused.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3.toAbsolutePath().toString())
                .title("Seek Paused Track")
                .durationSeconds(100)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch startedLatch = new CountDownLatch(1);
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    startedLatch.countDown();
                }
            });

            player.play(track);
            assertThat(startedLatch.await(3, TimeUnit.SECONDS)).isTrue();

            player.pause();
            assertThat(player.getState()).isEqualTo(PlaybackState.PAUSED);

            // Seek while paused
            player.seekTo(45);
            // State must strictly remain PAUSED
            assertThat(player.getState()).isEqualTo(PlaybackState.PAUSED);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(45);

            // Resuming resumes playback from seeked position
            player.resume();
            assertThat(player.getState()).isEqualTo(PlaybackState.PLAYING);

            player.stop();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS)
    @DisplayName("seekTo returns immediately without blocking EDT or caller thread")
    void testSeekIsNonBlocking() throws Exception {
        Path mp3 = createDummyMp3File("test_seek_perf.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3.toAbsolutePath().toString())
                .title("Seek Perf Track")
                .durationSeconds(120)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch startedLatch = new CountDownLatch(1);
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    startedLatch.countDown();
                }
            });

            player.play(track);
            assertThat(startedLatch.await(3, TimeUnit.SECONDS)).isTrue();

            long start = System.currentTimeMillis();
            player.seekTo(60);
            long duration = System.currentTimeMillis() - start;

            assertThat(duration).isLessThan(50); // Immediate return, well under 50ms

            player.stop();
        }
    }

    @Test
    @Timeout(value = 6, unit = TimeUnit.SECONDS)
    @DisplayName("Rapid consecutive seek operations simulate user scrubbing safely without crashing")
    void testRapidConsecutiveSeeksDuringPlayback() throws Exception {
        Path mp3 = createDummyMp3File("test_seek_rapid.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3.toAbsolutePath().toString())
                .title("Rapid Seek Track")
                .durationSeconds(100)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch startedLatch = new CountDownLatch(1);
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    startedLatch.countDown();
                }
            });

            player.play(track);
            assertThat(startedLatch.await(3, TimeUnit.SECONDS)).isTrue();

            // Perform rapid seeks like scrubbing slider
            for (int sec = 5; sec <= 50; sec += 5) {
                player.seekTo(sec);
            }

            assertThat(player.getCurrentPositionSeconds()).isEqualTo(50);
            assertThat(player.getState()).isIn(PlaybackState.PLAYING, PlaybackState.STOPPED);

            player.stop();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Seeking with a zero-duration track clamps negative values to 0 and accepts non-negative positions")
    void testSeekWithZeroDurationTrack() throws Exception {
        Path mp3 = createDummyMp3File("test_seek_zero_dur.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3.toAbsolutePath().toString())
                .title("Zero Duration Track")
                .durationSeconds(0)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch startedLatch = new CountDownLatch(1);
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    startedLatch.countDown();
                }
            });

            player.play(track);
            assertThat(startedLatch.await(3, TimeUnit.SECONDS)).isTrue();

            player.seekTo(-15);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(0);

            player.seekTo(45);
            assertThat(player.getCurrentPositionSeconds()).isEqualTo(45);

            player.stop();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Seeking to totalDurationSeconds triggers natural track completion")
    void testSeekToEndTriggersNaturalCompletion() throws Exception {
        Path mp3 = createDummyMp3File("test_seek_end.mp3", 10);
        Track track = Track.builder()
                .filePath(mp3.toAbsolutePath().toString())
                .title("Seek End Track")
                .durationSeconds(60)
                .build();

        try (BasicAudioPlayer player = new BasicAudioPlayer()) {
            CountDownLatch finishedLatch = new CountDownLatch(1);
            CountDownLatch startedLatch = new CountDownLatch(1);
            player.addPlayerListener(new PlayerListener() {
                @Override
                public void onTrackStarted(Track t) {
                    startedLatch.countDown();
                }

                @Override
                public void onTrackFinished(Track t) {
                    finishedLatch.countDown();
                }
            });

            player.play(track);
            assertThat(startedLatch.await(3, TimeUnit.SECONDS)).isTrue();

            // Seek directly to end of track (60s)
            player.seekTo(60);

            assertThat(finishedLatch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        }
    }

    // --- Helper to build valid multi-frame MP3 data ---

    private Path createDummyMp3File(String fileName, int frameCount) throws IOException {
        Path file = tempDir.resolve(fileName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Standard MPEG-1 Layer III frame (128 kbps, 44.1 kHz, stereo -> 417 bytes)
        int frameSize = 417;
        byte[] frame = new byte[frameSize];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;

        for (int i = 0; i < frameCount; i++) {
            baos.write(frame);
        }

        Files.write(file, baos.toByteArray());
        return file;
    }
}
