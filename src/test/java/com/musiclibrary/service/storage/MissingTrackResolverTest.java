package com.musiclibrary.service.storage;

import com.musiclibrary.model.Track;
import com.musiclibrary.service.importer.FileScanner;
import com.musiclibrary.service.metadata.MetadataExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MissingTrackResolverTest {

    @TempDir
    Path tempDir;

    private MissingTrackResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MissingTrackResolver();
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.shutdown();
        }
    }

    @Test
    @DisplayName("isMissing correctly identifies non-existent or blank files")
    void testIsMissing() throws IOException {
        Path existing = Files.createTempFile(tempDir, "existing", ".mp3");
        Track validTrack = Track.builder().id("1").filePath(existing.toString()).build();
        assertThat(MissingTrackResolver.isMissing(validTrack)).isFalse();

        Track missingTrack = Track.builder().id("2").filePath(tempDir.resolve("not_found.mp3").toString()).build();
        assertThat(MissingTrackResolver.isMissing(missingTrack)).isTrue();

        Track nullTrack = new Track();
        assertThat(MissingTrackResolver.isMissing(nullTrack)).isTrue();

        assertThat(MissingTrackResolver.isMissing(null)).isFalse();
    }

    @Test
    @DisplayName("findMissingTracks extracts all missing tracks and sets missing flag")
    void testFindMissingTracks() throws IOException {
        Path existing = Files.createTempFile(tempDir, "song", ".mp3");
        Track t1 = Track.builder().id("1").filePath(existing.toString()).build();
        Track t2 = Track.builder().id("2").filePath(tempDir.resolve("missing1.mp3").toString()).build();
        Track t3 = Track.builder().id("3").filePath(tempDir.resolve("missing2.mp3").toString()).build();

        List<Track> missing = resolver.findMissingTracks(List.of(t1, t2, t3));
        assertThat(missing).containsExactly(t2, t3);
        assertThat(t1.isMissing()).isFalse();
        assertThat(t2.isMissing()).isTrue();
        assertThat(t3.isMissing()).isTrue();
    }

    @Test
    @DisplayName("calculateMatchScore scores exact filename and file size matches correctly")
    void testCalculateMatchScore() throws IOException {
        Path candidate = tempDir.resolve("Bohemian Rhapsody.mp3");
        Files.write(candidate, new byte[1024]); // 1024 bytes

        Track track = Track.builder()
                .id("t1")
                .filePath("C:/old/music/Bohemian Rhapsody.mp3")
                .title("Bohemian Rhapsody")
                .artist("Queen")
                .fileSizeBytes(1024)
                .build();

        int score = resolver.calculateMatchScore(track, candidate, "Bohemian Rhapsody.mp3", "Bohemian Rhapsody");
        // Filename exact match (+40) + File size exact match (+30) = at least 70
        assertThat(score).isGreaterThanOrEqualTo(70);
    }

    @Test
    @DisplayName("findBestMatch picks candidate with highest score above threshold and respects exclusions")
    void testFindBestMatch() throws IOException {
        Path candidate1 = tempDir.resolve("Song One.mp3");
        Files.write(candidate1, new byte[500]);

        Path candidate2 = tempDir.resolve("Song Two.mp3");
        Files.write(candidate2, new byte[1000]);

        Track track = Track.builder()
                .id("t1")
                .filePath("C:/old/Song Two.mp3")
                .title("Song Two")
                .fileSizeBytes(1000)
                .build();

        Path match = resolver.findBestMatch(track, List.of(candidate1, candidate2), Collections.emptySet());
        assertThat(match).isEqualTo(candidate2);

        // When candidate2 is already excluded, candidate1 is not chosen if its score is too low
        Set<Path> excluded = new HashSet<>();
        excluded.add(candidate2);
        Path matchWithExclusion = resolver.findBestMatch(track, List.of(candidate1, candidate2), excluded);
        assertThat(matchWithExclusion).isNull();
    }

    @Test
    @DisplayName("relink updates file path, size, and clears missing flag while preserving metadata")
    void testRelink() throws IOException {
        Path newFile = tempDir.resolve("relocated.mp3");
        Files.write(newFile, new byte[2048]);

        Instant dateAdded = Instant.now().minusSeconds(1000);
        Track track = Track.builder()
                .id("track-abc")
                .filePath("C:/old/missing.mp3")
                .title("Keep This Title")
                .artist("Keep This Artist")
                .album("Keep This Album")
                .rating(5)
                .playCount(42)
                .dateAdded(dateAdded)
                .fileSizeBytes(500)
                .build();
        track.setMissing(true);

        boolean relinked = resolver.relink(track, newFile);
        assertThat(relinked).isTrue();
        assertThat(track.getFilePath()).isEqualTo(newFile.toAbsolutePath().normalize().toString());
        assertThat(track.getFileSizeBytes()).isEqualTo(2048);
        assertThat(track.isMissing()).isFalse();

        // Metadata preserved
        assertThat(track.getId()).isEqualTo("track-abc");
        assertThat(track.getTitle()).isEqualTo("Keep This Title");
        assertThat(track.getArtist()).isEqualTo("Keep This Artist");
        assertThat(track.getAlbum()).isEqualTo("Keep This Album");
        assertThat(track.getRating()).isEqualTo(5);
        assertThat(track.getPlayCount()).isEqualTo(42);
        assertThat(track.getDateAdded()).isEqualTo(dateAdded);
    }

    @Test
    @DisplayName("relink fails safely on non-existent path or directory")
    void testRelinkFailure() {
        Track track = Track.builder().id("1").filePath("old.mp3").build();
        assertThat(resolver.relink(track, tempDir.resolve("not_found.mp3"))).isFalse();
        assertThat(resolver.relink(track, tempDir)).isFalse();
        assertThat(resolver.relink(null, tempDir.resolve("test.mp3"))).isFalse();
    }

    @Test
    @DisplayName("resolveMissingTracksAsync scans folder and relinks multiple tracks asynchronously")
    void testResolveMissingTracksAsync() throws Exception {
        Path subDir = Files.createDirectories(tempDir.resolve("NewMusicFolder"));
        Path newSong1 = subDir.resolve("Track A.mp3");
        Files.write(newSong1, new byte[512]);

        Path newSong2 = subDir.resolve("Track B.mp3");
        Files.write(newSong2, new byte[1024]);

        Track t1 = Track.builder()
                .id("t1")
                .filePath("D:/Lost/Track A.mp3")
                .title("Track A")
                .fileSizeBytes(512)
                .build();

        Track t2 = Track.builder()
                .id("t2")
                .filePath("D:/Lost/Track B.mp3")
                .title("Track B")
                .fileSizeBytes(1024)
                .build();

        Track t3 = Track.builder()
                .id("t3")
                .filePath("D:/Lost/Track C.mp3")
                .title("Track C")
                .fileSizeBytes(2048)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger relinkedCounter = new AtomicInteger(0);
        AtomicInteger remainingCounter = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

        MissingTrackResolver.ResolveTask task = resolver.resolveMissingTracksAsync(
                List.of(t1, t2, t3),
                tempDir,
                new MissingTrackResolver.ResolveProgressListener() {
                    @Override
                    public void onProgress(int current, int total, Path currentPath) {}

                    @Override
                    public void onRelinked(Track track, Path newPath) {}

                    @Override
                    public void onComplete(int relinkedCount, int remainingCount) {
                        relinkedCounter.set(relinkedCount);
                        remainingCounter.set(remainingCount);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable error) {
                        errorRef.set(error);
                        latch.countDown();
                    }
                }
        );

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        if (errorRef.get() != null) {
            errorRef.get().printStackTrace();
        }
        assertThat(errorRef.get()).isNull();
        assertThat(completed).isTrue();
        assertThat(task.isDone()).isTrue();

        assertThat(relinkedCounter.get()).isEqualTo(2);
        assertThat(remainingCounter.get()).isEqualTo(1);

        assertThat(t1.isMissing()).isFalse();
        assertThat(t1.getFilePath()).isEqualTo(newSong1.toAbsolutePath().normalize().toString());
        assertThat(t2.isMissing()).isFalse();
        assertThat(t2.getFilePath()).isEqualTo(newSong2.toAbsolutePath().normalize().toString());
        assertThat(t3.isMissing()).isTrue(); // Track C had no match in tempDir
    }

    @Test
    @DisplayName("resolveMissingTracksAsync can be cancelled via task handle")
    void testResolveMissingTracksAsyncCancellation() {
        MissingTrackResolver.ResolveTask task = resolver.resolveMissingTracksAsync(
                List.of(Track.builder().id("1").filePath("none.mp3").build()),
                tempDir,
                null
        );
        task.cancel();
        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("calculateMatchScore and findBestMatch enforce MATCH_THRESHOLD (50) boundary accurately")
    void testMatchScoreThresholdBoundary() throws IOException {
        // Candidate with size 500 bytes and name "Track 1.mp3"
        Path cand1 = tempDir.resolve("Track 1.mp3");
        Files.write(cand1, new byte[500]);

        // Case A: Filename match (40) but file size mismatch (0) -> Score = 40 (< 50 threshold) -> Returns null
        Track trackBelowThreshold = Track.builder()
                .id("t-low")
                .filePath("C:/old/Track 1.mp3")
                .title("Unmatched Title")
                .fileSizeBytes(9999) // Different size
                .build();
        Path matchBelow = resolver.findBestMatch(trackBelowThreshold, List.of(cand1), Collections.emptySet());
        assertThat(matchBelow).isNull();

        // Case B: Base name match (35) + Partial overlap (no) + file size match (30) = 65 (>= 50 threshold) -> Matches!
        Track trackAboveThreshold = Track.builder()
                .id("t-high")
                .filePath("C:/old/Track 1.wav") // Base name "Track 1" matches cand1 "Track 1.mp3"
                .title("Unmatched Title")
                .fileSizeBytes(500)
                .build();
        Path matchAbove = resolver.findBestMatch(trackAboveThreshold, List.of(cand1), Collections.emptySet());
        assertThat(matchAbove).isEqualTo(cand1);

        // Case C: Partial match (15) + Size mismatch -> 15 (< 50) -> Returns null
        Track trackPartialOnly = Track.builder()
                .id("t-part")
                .filePath("C:/old/Track 1 Extra.mp3")
                .fileSizeBytes(800)
                .build();
        Path matchPartial = resolver.findBestMatch(trackPartialOnly, List.of(cand1), Collections.emptySet());
        assertThat(matchPartial).isNull();
    }

    @Test
    @DisplayName("findBestMatch selects candidate with highest score among competing candidates")
    void testMultipleCandidatesCompetitionAndExclusionCascade() throws IOException {
        // Create 3 candidate files:
        // candExact: "Song.mp3", 1000 bytes (Exact name 40 + size 30 = 70)
        Path candExact = tempDir.resolve("Song.mp3");
        Files.write(candExact, new byte[1000]);

        // candBase: "Song.flac", 1000 bytes (Base name 35 + size 30 = 65)
        Path candBase = tempDir.resolve("Song.flac");
        Files.write(candBase, new byte[1000]);

        // candPartial: "Song Remix.mp3", 2000 bytes (Partial 15 + size 0 = 15)
        Path candPartial = tempDir.resolve("Song Remix.mp3");
        Files.write(candPartial, new byte[2000]);

        Track targetTrack = Track.builder()
                .id("t-comp")
                .filePath("D:/Lost/Song.mp3")
                .title("Song")
                .fileSizeBytes(1000)
                .build();

        List<Path> candidates = List.of(candPartial, candExact, candBase);

        // 1. Without exclusions: candExact (Score 70) is picked over candBase (65)
        Path best = resolver.findBestMatch(targetTrack, candidates, Collections.emptySet());
        assertThat(best).isEqualTo(candExact);

        // 2. When candExact is excluded: candBase (Score 65 >= 50) is picked
        Set<Path> exclusions = new HashSet<>();
        exclusions.add(candExact);
        Path secondBest = resolver.findBestMatch(targetTrack, candidates, exclusions);
        assertThat(secondBest).isEqualTo(candBase);

        // 3. When candExact and candBase are excluded: candPartial (15 < 50) is rejected -> null
        exclusions.add(candBase);
        Path thirdBest = resolver.findBestMatch(targetTrack, candidates, exclusions);
        assertThat(thirdBest).isNull();
    }

    @Test
    @DisplayName("relink preserves complete track metadata, artwork, and playlist relationship non-destructively")
    void testRelinkNonDestructiveFullIntegrity() throws IOException {
        Path relocatedFile = tempDir.resolve("new_location.mp3");
        Files.write(relocatedFile, new byte[4096]);

        byte[] artwork = new byte[]{1, 2, 3, 4, 5};
        Instant dateAdded = Instant.ofEpochSecond(1600000000);

        Track originalTrack = Track.builder()
                .id("permanent-id-123")
                .filePath("C:/Lost/song.mp3")
                .title("Original Title")
                .artist("Original Artist")
                .album("Original Album")
                .genre("Progressive Rock")
                .year(1973)
                .trackNumber(7)
                .rating(4)
                .playCount(88)
                .dateAdded(dateAdded)
                .durationSeconds(420)
                .fileSizeBytes(1234)
                .artworkData(artwork)
                .build();
        originalTrack.setMissing(true);

        // In a Playlist
        com.musiclibrary.model.Playlist playlist = new com.musiclibrary.model.Playlist("Rock Classics");
        playlist.addTrack("permanent-id-123");

        // Relink
        boolean success = resolver.relink(originalTrack, relocatedFile);
        assertThat(success).isTrue();

        // Mutable file attributes updated:
        assertThat(originalTrack.getFilePath()).isEqualTo(relocatedFile.toAbsolutePath().normalize().toString());
        assertThat(originalTrack.getFileSizeBytes()).isEqualTo(4096);
        assertThat(originalTrack.isMissing()).isFalse();

        // All identity and music metadata strictly preserved:
        assertThat(originalTrack.getId()).isEqualTo("permanent-id-123");
        assertThat(originalTrack.getTitle()).isEqualTo("Original Title");
        assertThat(originalTrack.getArtist()).isEqualTo("Original Artist");
        assertThat(originalTrack.getAlbum()).isEqualTo("Original Album");
        assertThat(originalTrack.getGenre()).isEqualTo("Progressive Rock");
        assertThat(originalTrack.getYear()).isEqualTo(1973);
        assertThat(originalTrack.getTrackNumber()).isEqualTo(7);
        assertThat(originalTrack.getRating()).isEqualTo(4);
        assertThat(originalTrack.getPlayCount()).isEqualTo(88);
        assertThat(originalTrack.getDateAdded()).isEqualTo(dateAdded);
        assertThat(originalTrack.getDurationSeconds()).isEqualTo(420);
        assertThat(originalTrack.getArtworkData()).isEqualTo(artwork);

        // Playlist relationship remains intact:
        assertThat(playlist.getTrackIds()).contains("permanent-id-123");
    }

    @Test
    @DisplayName("resolveMissingTracksAsync safely handles abnormal inputs: non-existent folder, empty folder, null lists")
    void testResolveMissingTracksAsyncAbnormalInputs() throws Exception {
        Track missingTrack = Track.builder().id("m1").filePath("missing.mp3").build();

        // 1. Non-existent search folder
        Path nonExistent = tempDir.resolve("does_not_exist");
        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicInteger remainingCount1 = new AtomicInteger(-1);

        resolver.resolveMissingTracksAsync(List.of(missingTrack), nonExistent, new MissingTrackResolver.ResolveProgressListener() {
            @Override public void onProgress(int current, int total, Path currentPath) {}
            @Override public void onRelinked(Track track, Path newPath) {}
            @Override public void onComplete(int relinkedCount, int remainingCount) {
                remainingCount1.set(remainingCount);
                latch1.countDown();
            }
        });
        assertThat(latch1.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(remainingCount1.get()).isEqualTo(1);

        // 2. Empty directory
        Path emptyDir = Files.createDirectories(tempDir.resolve("empty_folder"));
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger relinked2 = new AtomicInteger(-1);

        resolver.resolveMissingTracksAsync(List.of(missingTrack), emptyDir, new MissingTrackResolver.ResolveProgressListener() {
            @Override public void onProgress(int current, int total, Path currentPath) {}
            @Override public void onRelinked(Track track, Path newPath) {}
            @Override public void onComplete(int relinkedCount, int remainingCount) {
                relinked2.set(relinkedCount);
                latch2.countDown();
            }
        });
        assertThat(latch2.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(relinked2.get()).isEqualTo(0);
        assertThat(missingTrack.isMissing()).isTrue();

        // 3. Null or empty missing track list
        CountDownLatch latch3 = new CountDownLatch(1);
        resolver.resolveMissingTracksAsync(Collections.emptyList(), tempDir, new MissingTrackResolver.ResolveProgressListener() {
            @Override public void onProgress(int current, int total, Path currentPath) {}
            @Override public void onRelinked(Track track, Path newPath) {}
            @Override public void onComplete(int relinkedCount, int remainingCount) {
                latch3.countDown();
            }
        });
        assertThat(latch3.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("resolveMissingTracksAsync cancel stops processing during iteration immediately")
    void testResolveMissingTracksAsyncCancellationMidFlight() throws Exception {
        // Create 10 candidate files
        Path searchDir = Files.createDirectories(tempDir.resolve("CancelTestDir"));
        for (int i = 1; i <= 5; i++) {
            Files.write(searchDir.resolve("Track_" + i + ".mp3"), new byte[100]);
        }

        // Create 10 missing tracks
        List<Track> missingTracks = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            missingTracks.add(Track.builder()
                    .id("can-" + i)
                    .filePath("C:/Old/Track_" + i + ".mp3")
                    .title("Track_" + i)
                    .fileSizeBytes(100)
                    .build());
        }

        AtomicInteger processedProgress = new AtomicInteger(0);
        CountDownLatch progressLatch = new CountDownLatch(2); // Wait until 2 items are processed
        AtomicReference<MissingTrackResolver.ResolveTask> taskRef = new AtomicReference<>();

        taskRef.set(resolver.resolveMissingTracksAsync(missingTracks, searchDir, new MissingTrackResolver.ResolveProgressListener() {
            @Override
            public void onProgress(int current, int total, Path currentPath) {
                int count = processedProgress.incrementAndGet();
                progressLatch.countDown();
                if (count >= 2 && taskRef.get() != null) {
                    taskRef.get().cancel();
                }
            }

            @Override
            public void onRelinked(Track track, Path newPath) {}

            @Override
            public void onComplete(int relinkedCount, int remainingCount) {}
        }));

        assertThat(progressLatch.await(5, TimeUnit.SECONDS)).isTrue();
        // Give background thread a short moment to observe cancellation
        Thread.sleep(100);

        assertThat(taskRef.get().isCancelled()).isTrue();
        assertThat(taskRef.get().isDone()).isTrue();
        // Should not have processed all 10
        assertThat(processedProgress.get()).isLessThan(10);
    }
}
