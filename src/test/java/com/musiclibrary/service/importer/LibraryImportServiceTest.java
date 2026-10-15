package com.musiclibrary.service.importer;

import com.musiclibrary.model.Library;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.metadata.MetadataExtractor;
import com.musiclibrary.service.storage.LibraryStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.musiclibrary.service.importer.LibraryImportService.ImportTask;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryImportServiceTest {

    @TempDir
    Path tempDir;

    private String originalStorageProperty;

    @BeforeEach
    void setUpStorageProperty() {
        originalStorageProperty = System.getProperty("musiclibrary.storage.path");
        System.setProperty("musiclibrary.storage.path", tempDir.resolve("isolated_test_storage.json").toString());
    }

    @AfterEach
    void restoreStorageProperty() {
        if (originalStorageProperty != null) {
            System.setProperty("musiclibrary.storage.path", originalStorageProperty);
        } else {
            System.clearProperty("musiclibrary.storage.path");
        }
    }

    @Test
    @DisplayName("scanAndImportAsync discovers files, commits batches, saves library, and completes via listener and future")
    void testSuccessfulImportFlow() throws Exception {
        Library library = new Library();
        Path storageFile = tempDir.resolve("library.json");

        // Create 3 dummy mp3 files
        Files.createFile(tempDir.resolve("01 - Song One.mp3"));
        Files.createFile(tempDir.resolve("02 - Song Two.mp3"));
        Files.createFile(tempDir.resolve("03 - Song Three.mp3"));

        TestStorage mockStorage = new TestStorage();

        LibraryImportService service = new LibraryImportService(
                library,
                mockStorage,
                storageFile,
                new FileScanner(),
                new DummyMetadataExtractor(),
                2 // batch size 2 to test batch commit
        );

        CountDownLatch latch = new CountDownLatch(1);
        List<Track> batchReceived = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completedCount = new AtomicInteger(-1);

        var task = service.scanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override
            public void onProgress(int processedCount, int totalCount, String currentFileName) {
            }

            @Override
            public void onBatchImported(List<Track> importedBatch) {
                batchReceived.addAll(importedBatch);
            }

            @Override
            public void onCompleted(int totalImportedCount) {
                completedCount.set(totalImportedCount);
                latch.countDown();
            }

            @Override
            public void onCancelled(int partialImportedCount) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(completedCount.get()).isEqualTo(3);
        assertThat(task.getFuture().get(1, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(library.getTrackCount()).isEqualTo(3);
        assertThat(batchReceived).hasSize(3);
        assertThat(mockStorage.saved).isTrue();
    }

    @Test
    @DisplayName("Deduplication skips tracks already present in the library while importing new ones")
    void testDeduplicationWithExistingTracks() throws Exception {
        Library library = new Library();
        Path existingSong = Files.createFile(tempDir.resolve("existing_song.mp3"));
        Path newSong1 = Files.createFile(tempDir.resolve("new_song_1.mp3"));
        Path newSong2 = Files.createFile(tempDir.resolve("new_song_2.mp3"));

        // Pre-populate library with existingSong with identical lastModified
        Track existingTrack = Track.builder()
                .filePath(existingSong.toAbsolutePath().normalize().toString())
                .title("Pre-existing Song")
                .lastModified(Files.getLastModifiedTime(existingSong).toInstant())
                .build();
        library.addTrack(existingTrack);

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                new DummyMetadataExtractor(),
                10
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger importedCount = new AtomicInteger(-1);

        var task = service.scanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) {
                importedCount.set(total);
                latch.countDown();
            }
            @Override
            public void onCancelled(int partial) { latch.countDown(); }
            @Override
            public void onError(Throwable error) { latch.countDown(); }
        });

        latch.await(5, TimeUnit.SECONDS);
        // Only 2 new tracks should be imported, existing one skipped
        assertThat(importedCount.get()).isEqualTo(2);
        assertThat(library.getTrackCount()).isEqualTo(3);

        ImportResult result = task.getResultFuture().get(1, TimeUnit.SECONDS);
        assertThat(result.addedCount()).isEqualTo(2);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Upsert updates existing track metadata while preserving stats when file lastModified is newer")
    void testUpsertWhenExistingFileModified() throws Exception {
        Library library = new Library();
        Path songFile = Files.createFile(tempDir.resolve("modified_song.mp3"));

        java.time.Instant oldModified = java.time.Instant.parse("2024-01-01T00:00:00Z");
        java.time.Instant origAdded = java.time.Instant.parse("2024-01-01T12:00:00Z");

        Track originalTrack = Track.builder()
                .id("orig-track-id")
                .filePath(songFile.toAbsolutePath().normalize().toString())
                .title("Old Title")
                .artist("Old Artist")
                .rating(5)
                .playCount(77)
                .dateAdded(origAdded)
                .lastModified(oldModified)
                .build();
        library.addTrack(originalTrack);

        // Metadata extractor returning updated metadata
        MetadataExtractor updatingExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title("Brand New Updated Title")
                        .artist("Updated Artist")
                        .album("Updated Album")
                        .rating(0)
                        .playCount(0)
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                updatingExtractor,
                10
        );

        var task = service.scanAndImportAsync(songFile, null);
        ImportResult result = task.getResultFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.addedCount()).isEqualTo(0);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);

        Track updatedInLibrary = library.getTrack("orig-track-id");
        assertThat(updatedInLibrary).isNotNull();
        assertThat(updatedInLibrary.getTitle()).isEqualTo("Brand New Updated Title");
        assertThat(updatedInLibrary.getArtist()).isEqualTo("Updated Artist");
        // User statistics must be preserved
        assertThat(updatedInLibrary.getRating()).isEqualTo(5);
        assertThat(updatedInLibrary.getPlayCount()).isEqualTo(77);
        assertThat(updatedInLibrary.getDateAdded()).isEqualTo(origAdded);
        assertThat(updatedInLibrary.getLastModified()).isAfter(oldModified);
    }

    @Test
    @DisplayName("Content duplicate (same title, artist, duration, size at different path) skipped under SKIP_SAME_CONTENT")
    void testContentDuplicateSkippedByDefaultPolicy() throws Exception {
        Library library = new Library();
        Path originalFile = Files.createFile(tempDir.resolve("orig.mp3"));
        Path duplicateFile = Files.createFile(tempDir.resolve("copy.mp3"));

        Track originalTrack = Track.builder()
                .filePath(originalFile.toAbsolutePath().normalize().toString())
                .title("Identical Song")
                .artist("Identical Artist")
                .durationSeconds(240)
                .fileSizeBytes(5000000)
                .lastModified(Files.getLastModifiedTime(originalFile).toInstant())
                .build();
        library.addTrack(originalTrack);

        MetadataExtractor extractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title("Identical Song")
                        .artist("Identical Artist")
                        .durationSeconds(240)
                        .fileSizeBytes(5000000)
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                extractor,
                10,
                DuplicatePolicy.SKIP_SAME_CONTENT
        );

        var task = service.scanAndImportAsync(duplicateFile, null);
        ImportResult result = task.getResultFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.addedCount()).isEqualTo(0);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(library.getTrackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Content duplicate allowed when DuplicatePolicy is ALLOW_SAME_CONTENT")
    void testContentDuplicateAllowedWhenPolicyAllows() throws Exception {
        Library library = new Library();
        Path originalFile = Files.createFile(tempDir.resolve("orig.mp3"));
        Path duplicateFile = Files.createFile(tempDir.resolve("copy.mp3"));

        Track originalTrack = Track.builder()
                .filePath(originalFile.toAbsolutePath().normalize().toString())
                .title("Identical Song")
                .artist("Identical Artist")
                .durationSeconds(240)
                .fileSizeBytes(5000000)
                .lastModified(Files.getLastModifiedTime(originalFile).toInstant())
                .build();
        library.addTrack(originalTrack);

        MetadataExtractor extractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title("Identical Song")
                        .artist("Identical Artist")
                        .durationSeconds(240)
                        .fileSizeBytes(5000000)
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                extractor,
                10,
                DuplicatePolicy.ALLOW_SAME_CONTENT
        );

        var task = service.scanAndImportAsync(duplicateFile, null);
        ImportResult result = task.getResultFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(library.getTrackCount()).isEqualTo(2);
    }


    @Test
    @DisplayName("Cancellation safely halts processing, retains partially imported tracks, and saves library")
    void testCancellationPreservesPartialImports() throws Exception {
        Library library = new Library();
        for (int i = 0; i < 15; i++) {
            Files.createFile(tempDir.resolve("song_" + i + ".mp3"));
        }

        TestStorage mockStorage = new TestStorage();

        LibraryImportService service = new LibraryImportService(
                library,
                mockStorage,
                tempDir.resolve("library.json"),
                new FileScanner(),
                new SlowMetadataExtractor(),
                1 // batch size 1 to ensure at least some tracks are committed before cancel
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean cancelledReported = new AtomicBoolean(false);
        AtomicInteger partialCount = new AtomicInteger(-1);

        var task = service.scanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) { latch.countDown(); }
            @Override
            public void onCancelled(int count) {
                cancelledReported.set(true);
                partialCount.set(count);
                latch.countDown();
            }
            @Override
            public void onError(Throwable error) { latch.countDown(); }
        });

        // Let 2-3 tracks process then cancel
        Thread.sleep(60);
        task.cancel();

        latch.await(5, TimeUnit.SECONDS);
        assertThat(cancelledReported.get()).isTrue();
        assertThat(task.isCancelled()).isTrue();
        assertThat(partialCount.get()).isGreaterThanOrEqualTo(0);
        assertThat(library.getTrackCount()).isEqualTo(partialCount.get());
        assertThat(mockStorage.saved).isTrue();
    }

    @Test
    @DisplayName("scanAndImportAsync handles empty, non-audio, or non-existent paths gracefully")
    void testEmptyAndNonAudioPaths() throws Exception {
        Library library = new Library();
        LibraryImportService service = new LibraryImportService(library, tempDir.resolve("empty_test_lib.json"));

        // 1. Non-existent path
        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicInteger count1 = new AtomicInteger(-1);
        service.scanAndImportAsync(tempDir.resolve("non_existent"), new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) {
                count1.set(total);
                latch1.countDown();
            }
            @Override
            public void onCancelled(int partial) { latch1.countDown(); }
            @Override
            public void onError(Throwable error) { latch1.countDown(); }
        });
        latch1.await(5, TimeUnit.SECONDS);
        assertThat(count1.get()).isEqualTo(0);

        // 2. Directory with only non-audio files
        Path nonAudioDir = Files.createDirectory(tempDir.resolve("docs"));
        Files.createFile(nonAudioDir.resolve("readme.txt"));
        Files.createFile(nonAudioDir.resolve("image.png"));

        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicInteger count2 = new AtomicInteger(-1);
        service.scanAndImportAsync(nonAudioDir, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) {
                count2.set(total);
                latch2.countDown();
            }
            @Override
            public void onCancelled(int partial) { latch2.countDown(); }
            @Override
            public void onError(Throwable error) { latch2.countDown(); }
        });
        latch2.await(5, TimeUnit.SECONDS);
        assertThat(count2.get()).isEqualTo(0);

        // 3. Null path
        CountDownLatch latch3 = new CountDownLatch(1);
        AtomicInteger count3 = new AtomicInteger(-1);
        service.scanAndImportAsync((Path) null, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) {
                count3.set(total);
                latch3.countDown();
            }
            @Override
            public void onCancelled(int partial) { latch3.countDown(); }
            @Override
            public void onError(Throwable error) { latch3.countDown(); }
        });
        latch3.await(5, TimeUnit.SECONDS);
        assertThat(count3.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Corrupt files that throw extraction exceptions are skipped while valid tracks succeed")
    void testCorruptedFilesResilience() throws Exception {
        Library library = new Library();
        Path validSong1 = Files.createFile(tempDir.resolve("valid1.mp3"));
        Path corruptSong = Files.createFile(tempDir.resolve("corrupt.mp3"));
        Path validSong2 = Files.createFile(tempDir.resolve("valid2.mp3"));

        MetadataExtractor selectiveExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) throws IOException {
                if (filePath.getFileName().toString().contains("corrupt")) {
                    throw new IOException("Corrupted audio header simulated");
                }
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title(filePath.getFileName().toString())
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                selectiveExtractor,
                10
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger completedCount = new AtomicInteger(-1);

        service.scanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) {
                completedCount.set(total);
                latch.countDown();
            }
            @Override
            public void onCancelled(int partial) { latch.countDown(); }
            @Override
            public void onError(Throwable error) { latch.countDown(); }
        });

        latch.await(5, TimeUnit.SECONDS);
        assertThat(completedCount.get()).isEqualTo(2);
        assertThat(library.getTrackCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Storage save failure during intermediate saves does not abort import process")
    void testStorageSaveFailureResilience() throws Exception {
        Library library = new Library();
        Files.createFile(tempDir.resolve("song1.mp3"));
        Files.createFile(tempDir.resolve("song2.mp3"));

        LibraryStorage failingStorage = new LibraryStorage() {
            @Override
            public void save(Library library, Path targetPath) throws IOException {
                throw new IOException("Simulated disk write failure");
            }

            @Override
            public Library load(Path sourcePath) {
                return new Library();
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                failingStorage,
                tempDir.resolve("library.json"),
                new FileScanner(),
                new DummyMetadataExtractor(),
                1
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger completedCount = new AtomicInteger(-1);

        service.scanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int total) {
                completedCount.set(total);
                latch.countDown();
            }
            @Override
            public void onCancelled(int partial) { latch.countDown(); }
            @Override
            public void onError(Throwable error) { latch.countDown(); }
        });

        latch.await(5, TimeUnit.SECONDS);
        assertThat(completedCount.get()).isEqualTo(2);
        assertThat(library.getTrackCount()).isEqualTo(2);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("scanAndImportAsync returns immediately without blocking caller thread")
    void testNonBlockingCallerThread() {
        Library library = new Library();
        LibraryImportService service = new LibraryImportService(library, tempDir.resolve("nonblocking_lib.json"));

        long start = System.currentTimeMillis();
        var task = service.scanAndImportAsync(tempDir, null);
        long duration = System.currentTimeMillis() - start;

        assertThat(task).isNotNull();
        assertThat(duration).isLessThan(200); // Must return asynchronously in under 200ms
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("scanAndImportAsync with multi-path collection imports files across paths")
    void testMultiPathScanAndImportAsync() throws Exception {
        Library library = new Library();
        Path file1 = Files.createFile(tempDir.resolve("song1.mp3"));
        Path subDir = Files.createDirectories(tempDir.resolve("sub_dir"));
        Path file2 = Files.createFile(subDir.resolve("song2.mp3"));

        TestStorage mockStorage = new TestStorage();
        LibraryImportService service = new LibraryImportService(
                library,
                mockStorage,
                tempDir.resolve("lib.json"),
                new FileScanner(),
                new DummyMetadataExtractor(),
                10
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger importedTotal = new AtomicInteger(0);

        var task = service.scanAndImportAsync(List.of(file1, subDir), new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int count) {
                importedTotal.set(count);
                latch.countDown();
            }
            @Override
            public void onCancelled(int count) {}
            @Override
            public void onError(Throwable error) {}
        });

        int futureResult = task.getFuture().get(4, TimeUnit.SECONDS);
        boolean finished = latch.await(4, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(futureResult).isEqualTo(2);
        assertThat(importedTotal.get()).isEqualTo(2);
        assertThat(library.getTrackCount()).isEqualTo(2);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("scanAndImportAsync with null or empty collection immediately completes with zero")
    void testEmptyOrNullCollectionImportAsync() throws Exception {
        Library library = new Library();
        LibraryImportService service = new LibraryImportService(library, tempDir.resolve("empty_coll_lib.json"));

        // Null collection
        var taskNull = service.scanAndImportAsync((java.util.Collection<Path>) null, null);
        assertThat(taskNull.getFuture().get(2, TimeUnit.SECONDS)).isEqualTo(0);

        // Empty collection
        var taskEmpty = service.scanAndImportAsync(Collections.emptyList(), null);
        assertThat(taskEmpty.getFuture().get(2, TimeUnit.SECONDS)).isEqualTo(0);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("onCompleted callback receives detailed ImportResult record")
    void testListenerReceivesDetailedImportResult() throws Exception {
        Library library = new Library();
        Path file1 = Files.createFile(tempDir.resolve("track_A.mp3"));

        CountDownLatch latch = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<ImportResult> receivedResult = new java.util.concurrent.atomic.AtomicReference<>();

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                new DummyMetadataExtractor(),
                10
        );

        var task = service.scanAndImportAsync(file1, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int count) {}
            @Override
            public void onCompleted(ImportResult result) {
                receivedResult.set(result);
                latch.countDown();
            }
            @Override
            public void onCancelled(int count) {}
            @Override
            public void onError(Throwable error) {}
        });

        boolean finished = latch.await(4, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(receivedResult.get()).isNotNull();
        assertThat(receivedResult.get().addedCount()).isEqualTo(1);
        assertThat(receivedResult.get().updatedCount()).isEqualTo(0);
        assertThat(receivedResult.get().skippedCount()).isEqualTo(0);
        assertThat(receivedResult.get().getTotalImportedCount()).isEqualTo(1);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Corrupt files that throw extraction exceptions are counted as skippedCount")
    void testCorruptFileIncrementsSkippedCount() throws Exception {
        Library library = new Library();
        Path validFile = Files.createFile(tempDir.resolve("valid.mp3"));
        Path corruptFile = Files.createFile(tempDir.resolve("corrupt.mp3"));

        MetadataExtractor failingExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) throws java.io.IOException {
                if (filePath.getFileName().toString().contains("corrupt")) {
                    throw new java.io.IOException("Corrupt audio header");
                }
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title("Valid Track")
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                failingExtractor,
                10
        );

        var task = service.scanAndImportAsync(List.of(validFile, corruptFile), null);
        ImportResult result = task.getResultFuture().get(4, TimeUnit.SECONDS);

        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(library.getTrackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Parallel import handles batch of 100 tracks with multi-threaded extraction and throttled progress")
    void testParallelBatchImportScalability() throws Exception {
        Library library = new Library();
        List<Path> files = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            files.add(Files.createFile(tempDir.resolve(String.format("bulk_track_%03d.mp3", i))));
        }

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                new DummyMetadataExtractor(),
                25
        );

        CountDownLatch latch = new CountDownLatch(1);
        List<Track> batchTracks = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger progressCallCount = new AtomicInteger(0);

        var task = service.scanAndImportAsync(files, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {
                progressCallCount.incrementAndGet();
            }

            @Override
            public void onBatchImported(List<Track> batch) {
                batchTracks.addAll(batch);
            }

            @Override
            public void onCompleted(int total) {
                latch.countDown();
            }

            @Override
            public void onCancelled(int count) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(task.getResultFuture().get(1, TimeUnit.SECONDS).addedCount()).isEqualTo(100);
        assertThat(library.getTrackCount()).isEqualTo(100);
        assertThat(batchTracks).hasSize(100);
        assertThat(progressCallCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("shutdown, shutdownNow, and isShutdown manage executorService lifecycle")
    void testShutdownLifecycle() {
        Library library = new Library();
        LibraryImportService service = new LibraryImportService(library, tempDir.resolve("shutdown_lib1.json"));

        assertThat(service.isShutdown()).isFalse();

        service.shutdown();
        assertThat(service.isShutdown()).isTrue();

        LibraryImportService service2 = new LibraryImportService(library, tempDir.resolve("shutdown_lib2.json"));
        assertThat(service2.isShutdown()).isFalse();
        service2.shutdownNow();
        assertThat(service2.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("Parallel import produces deterministic results with mixed new, duplicate, and updated files")
    void testParallelImportDeterminismWithMixedContent() throws Exception {
        Library library = new Library();
        Path baseDir = tempDir.resolve("determinism_test");
        Files.createDirectories(baseDir);

        // Pre-populate library with:
        // 1. 50 tracks that will have content duplicates in the import batch
        for (int i = 0; i < 50; i++) {
            library.addTrack(Track.builder()
                    .id("existing-content-" + i)
                    .filePath("C:/OldLibrary/Existing_" + i + ".mp3")
                    .title("Shared Song " + i)
                    .artist("Artist " + i)
                    .durationSeconds(200)
                    .fileSizeBytes(4000000L)
                    .build());
        }

        // 2. 50 tracks that will be updated in the import batch (same path, older timestamp)
        List<Path> updateFiles = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            Path f = Files.createFile(baseDir.resolve(String.format("update_track_%03d.mp3", i)));
            updateFiles.add(f);
            library.addTrack(Track.builder()
                    .id("existing-path-" + i)
                    .filePath(f.toAbsolutePath().normalize().toString())
                    .title("Old Title " + i)
                    .artist("Old Artist " + i)
                    .lastModified(Instant.ofEpochMilli(1000L)) // Very old timestamp
                    .build());
        }

        // Create the import batch:
        // - 50 new files
        // - 50 content duplicate files (matching existing-content-*)
        // - 50 update files (matching existing-path-*)
        List<Path> importFiles = new ArrayList<>(150);
        for (int i = 0; i < 50; i++) {
            importFiles.add(Files.createFile(baseDir.resolve(String.format("new_track_%03d.mp3", i))));
        }
        for (int i = 0; i < 50; i++) {
            importFiles.add(Files.createFile(baseDir.resolve(String.format("dup_track_%03d.mp3", i))));
        }
        importFiles.addAll(updateFiles);

        MetadataExtractor extractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                String fn = filePath.getFileName().toString();
                if (fn.startsWith("new_track_")) {
                    return Track.builder()
                            .filePath(filePath.toAbsolutePath().normalize().toString())
                            .title("New Song " + fn)
                            .artist("New Artist")
                            .durationSeconds(180)
                            .fileSizeBytes(3500000L)
                            .build();
                } else if (fn.startsWith("dup_track_")) {
                    int idx = Integer.parseInt(fn.substring("dup_track_".length(), fn.indexOf('.')));
                    return Track.builder()
                            .filePath(filePath.toAbsolutePath().normalize().toString())
                            .title("Shared Song " + idx)
                            .artist("Artist " + idx)
                            .durationSeconds(200)
                            .fileSizeBytes(4000000L)
                            .build();
                } else { // update_track_
                    return Track.builder()
                            .filePath(filePath.toAbsolutePath().normalize().toString())
                            .title("Updated Title " + fn)
                            .artist("Updated Artist")
                            .build();
                }
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                extractor,
                20,
                DuplicatePolicy.SKIP_SAME_CONTENT
        );

        ImportTask task = service.scanAndImportAsync(importFiles, null);
        ImportResult result = task.getResultFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.addedCount()).isEqualTo(50);
        assertThat(result.skippedCount()).isEqualTo(50);
        assertThat(result.updatedCount()).isEqualTo(50);
        assertThat(result.getTotalImportedCount()).isEqualTo(100);
        // Initial 100 tracks + 50 new = 150 tracks
        assertThat(library.getTrackCount()).isEqualTo(150);
    }

    @Test
    @DisplayName("Immediate cancellation during parallel import stops pipeline early and cleans up safely")
    void testParallelImportImmediateCancellationAndResourceCleanup() throws Exception {
        Library library = new Library();
        Path baseDir = tempDir.resolve("cancel_test");
        Files.createDirectories(baseDir);

        List<Path> files = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            files.add(Files.createFile(baseDir.resolve(String.format("cancel_track_%03d.mp3", i))));
        }

        CountDownLatch cancelledLatch = new CountDownLatch(1);
        AtomicBoolean onCancelledCalled = new AtomicBoolean(false);

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                new SlowMetadataExtractor(), // 25ms per extraction
                10
        );

        ImportTask task = service.scanAndImportAsync(files, new ImportProgressListener() {
            @Override
            public void onProgress(int processed, int total, String file) {}
            @Override
            public void onBatchImported(List<Track> batch) {}
            @Override
            public void onCompleted(int count) {}
            @Override
            public void onCancelled(int count) {}
            @Override
            public void onCancelled(ImportResult result) {
                onCancelledCalled.set(true);
                cancelledLatch.countDown();
            }
            @Override
            public void onError(Throwable error) {}
        });

        // Cancel after 40ms to interrupt early while window is processing
        Thread.sleep(40);
        task.cancel();

        boolean finished = cancelledLatch.await(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(task.isCancelled()).isTrue();
        assertThat(onCancelledCalled.get()).isTrue();

        ImportResult result = task.getResultFuture().get(2, TimeUnit.SECONDS);
        // It must NOT have processed all 100 tracks
        assertThat(result.getTotalImportedCount()).isLessThan(100);
        assertThat(library.getTrackCount()).isLessThan(100);
    }

    @Test
    @DisplayName("Unmodified existing tracks bypass metadata extraction completely via short-circuit check")
    void testUnmodifiedFilesSkipMetadataExtractionShortCircuit() throws Exception {
        Library library = new Library();
        Path baseDir = tempDir.resolve("unmodified_test");
        Files.createDirectories(baseDir);

        List<Path> files = new ArrayList<>(50);
        Instant now = Instant.now();

        for (int i = 0; i < 50; i++) {
            Path f = Files.createFile(baseDir.resolve(String.format("unmodified_%03d.mp3", i)));
            files.add(f);

            // Register in library with identical path and lastModified in the future/present
            library.addTrack(Track.builder()
                    .id("unmod-" + i)
                    .filePath(f.toAbsolutePath().normalize().toString())
                    .title("Track " + i)
                    .artist("Artist " + i)
                    .lastModified(now.plusSeconds(100)) // Guarantee file on disk is not newer
                    .build());
        }

        AtomicInteger extractionCallCount = new AtomicInteger(0);
        MetadataExtractor countingExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                extractionCallCount.incrementAndGet();
                return Track.builder().filePath(filePath.toString()).title("Dummy").build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                countingExtractor,
                10
        );

        ImportTask task = service.scanAndImportAsync(files, null);
        ImportResult result = task.getResultFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.addedCount()).isEqualTo(0);
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(50);
        // Core requirement: extraction was short-circuited 100%, never called!
        assertThat(extractionCallCount.get()).isEqualTo(0);
        assertThat(library.getTrackCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("rescanAndImportAsync forces metadata re-extraction, updates new fields via upsertTrack, preserves user stats, and imports new tracks")
    void testRescanAndImportForceMetadataRefresh() throws Exception {
        Library library = new Library();
        Path existingFile = Files.createFile(tempDir.resolve("existing_track.mp3"));
        Path newFile = Files.createFile(tempDir.resolve("new_track.mp3"));

        Instant fileModTime = Files.getLastModifiedTime(existingFile).toInstant();
        Instant originalDateAdded = Instant.now().minusSeconds(86400);

        Track existing = Track.builder()
                .id("track-abc")
                .filePath(existingFile.toAbsolutePath().normalize().toString())
                .title("Initial Title")
                .artist("Initial Artist")
                .rating(5)
                .playCount(42)
                .dateAdded(originalDateAdded)
                .lastModified(fileModTime) // Matches file modification time exactly
                .bpm(0)
                .bitrate(0)
                .albumArtist("")
                .discNumber(0)
                .build();
        library.addTrack(existing);

        MetadataExtractor refreshingExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                if (filePath.equals(existingFile)) {
                    return Track.builder()
                            .filePath(filePath.toAbsolutePath().normalize().toString())
                            .title("Updated Title")
                            .artist("Updated Artist")
                            .albumArtist("Updated Album Artist")
                            .bpm(128)
                            .bitrate(320)
                            .discNumber(2)
                            .genre("Electronic")
                            .year(2026)
                            .build();
                } else {
                    return Track.builder()
                            .filePath(filePath.toAbsolutePath().normalize().toString())
                            .title("New Song")
                            .artist("New Artist")
                            .bpm(90)
                            .bitrate(256)
                            .build();
                }
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        TestStorage mockStorage = new TestStorage();
        LibraryImportService service = new LibraryImportService(
                library,
                mockStorage,
                tempDir.resolve("library.json"),
                new FileScanner(),
                refreshingExtractor,
                10
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ImportResult> resultRef = new AtomicReference<>();

        service.rescanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override public void onProgress(int processed, int total, String file) {}
            @Override public void onBatchImported(List<Track> batch) {}
            @Override public void onCompleted(int total) {}
            @Override
            public void onCompleted(ImportResult result) {
                resultRef.set(result);
                latch.countDown();
            }
            @Override public void onCancelled(int count) { latch.countDown(); }
            @Override public void onError(Throwable error) { latch.countDown(); }
        });

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        ImportResult result = resultRef.get();
        assertThat(result).isNotNull();
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.addedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(0);

        assertThat(library.getTrackCount()).isEqualTo(2);

        // Verify existing track was updated via upsertTrack, preserving user statistics
        Track updatedTrack = library.getTrack("track-abc");
        assertThat(updatedTrack).isNotNull();
        assertThat(updatedTrack.getTitle()).isEqualTo("Updated Title");
        assertThat(updatedTrack.getArtist()).isEqualTo("Updated Artist");
        assertThat(updatedTrack.getAlbumArtist()).isEqualTo("Updated Album Artist");
        assertThat(updatedTrack.getBpm()).isEqualTo(128);
        assertThat(updatedTrack.getBitrate()).isEqualTo(320);
        assertThat(updatedTrack.getDiscNumber()).isEqualTo(2);
        assertThat(updatedTrack.getGenre()).isEqualTo("Electronic");
        assertThat(updatedTrack.getYear()).isEqualTo(2026);

        // Protected user statistics
        assertThat(updatedTrack.getRating()).isEqualTo(5);
        assertThat(updatedTrack.getPlayCount()).isEqualTo(42);
        assertThat(updatedTrack.getDateAdded()).isEqualTo(originalDateAdded);

        // Verify new track was added
        Track newTrack = library.findByFilePath(newFile.toAbsolutePath().normalize().toString());
        assertThat(newTrack).isNotNull();
        assertThat(newTrack.getTitle()).isEqualTo("New Song");
        assertThat(newTrack.getBpm()).isEqualTo(90);
        assertThat(newTrack.getBitrate()).isEqualTo(256);

        assertThat(mockStorage.saved).isTrue();
    }

    @Test
    @DisplayName("rescanAndImportAsync cancels gracefully when task.cancel() is invoked")
    void testRescanAndImportCancellation() throws Exception {
        Library library = new Library();
        // Create 20 dummy mp3 files
        for (int i = 0; i < 20; i++) {
            Files.createFile(tempDir.resolve("track_" + i + ".mp3"));
        }

        CountDownLatch firstExtracted = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean cancelledFired = new AtomicBoolean(false);

        MetadataExtractor slowExtractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                firstExtracted.countDown();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                return Track.builder().filePath(filePath.toString()).title("Song").build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                slowExtractor,
                5
        );

        ImportTask task = service.rescanAndImportAsync(tempDir, new ImportProgressListener() {
            @Override public void onProgress(int processed, int total, String file) {}
            @Override public void onBatchImported(List<Track> batch) {}
            @Override public void onCompleted(int total) { latch.countDown(); }
            @Override public void onCompleted(ImportResult result) { latch.countDown(); }
            @Override
            public void onCancelled(int count) {
                cancelledFired.set(true);
                latch.countDown();
            }
            @Override public void onError(Throwable error) { latch.countDown(); }
        });

        // Wait for extraction to start, then cancel
        assertThat(firstExtracted.await(5, TimeUnit.SECONDS)).isTrue();
        task.cancel();

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(task.isCancelled()).isTrue();
        assertThat(cancelledFired.get()).isTrue();
    }

    @Test
    @DisplayName("rescanAndImportAsync safely handles empty directory, non-existent directory, and null paths")
    void testRescanAndImportEmptyOrNonExistentDirectory() throws Exception {
        Library library = new Library();
        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("library.json"),
                new FileScanner(),
                new DummyMetadataExtractor(),
                10
        );

        // 1. Empty directory
        Path emptyDir = Files.createDirectory(tempDir.resolve("empty_folder"));
        CountDownLatch latch1 = new CountDownLatch(1);
        AtomicReference<ImportResult> result1 = new AtomicReference<>();
        service.rescanAndImportAsync(emptyDir, new ImportProgressListener() {
            @Override public void onProgress(int p, int t, String f) {}
            @Override public void onBatchImported(List<Track> b) {}
            @Override public void onCompleted(int total) {}
            @Override
            public void onCompleted(ImportResult result) {
                result1.set(result);
                latch1.countDown();
            }
            @Override public void onCancelled(int c) { latch1.countDown(); }
            @Override public void onError(Throwable error) { latch1.countDown(); }
        });
        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result1.get()).isNotNull();
        assertThat(result1.get().addedCount()).isEqualTo(0);
        assertThat(result1.get().updatedCount()).isEqualTo(0);
        assertThat(result1.get().skippedCount()).isEqualTo(0);

        // 2. Non-existent directory
        Path nonExistent = tempDir.resolve("does_not_exist_dir");
        CountDownLatch latch2 = new CountDownLatch(1);
        AtomicReference<ImportResult> result2 = new AtomicReference<>();
        service.rescanAndImportAsync(nonExistent, new ImportProgressListener() {
            @Override public void onProgress(int p, int t, String f) {}
            @Override public void onBatchImported(List<Track> b) {}
            @Override public void onCompleted(int total) {}
            @Override
            public void onCompleted(ImportResult result) {
                result2.set(result);
                latch2.countDown();
            }
            @Override public void onCancelled(int c) { latch2.countDown(); }
            @Override public void onError(Throwable error) { latch2.countDown(); }
        });
        assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result2.get()).isNotNull();
        assertThat(result2.get().addedCount()).isEqualTo(0);
        assertThat(result2.get().updatedCount()).isEqualTo(0);

        // 3. Null rootPath
        CountDownLatch latch3 = new CountDownLatch(1);
        AtomicReference<ImportResult> result3 = new AtomicReference<>();
        service.rescanAndImportAsync((Path) null, new ImportProgressListener() {
            @Override public void onProgress(int p, int t, String f) {}
            @Override public void onBatchImported(List<Track> b) {}
            @Override public void onCompleted(int total) {}
            @Override
            public void onCompleted(ImportResult result) {
                result3.set(result);
                latch3.countDown();
            }
            @Override public void onCancelled(int c) { latch3.countDown(); }
            @Override public void onError(Throwable error) { latch3.countDown(); }
        });
        assertThat(latch3.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result3.get()).isNotNull();
        assertThat(result3.get().addedCount()).isEqualTo(0);
        assertThat(result3.get().updatedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("rescan forces re-extraction even if disk file lastModified is in the past compared to library")
    void testRescanAndImportPastLastModifiedRefresh() throws Exception {
        Library library = new Library();
        Path file = Files.createFile(tempDir.resolve("past_track.mp3"));

        Instant now = Instant.now();
        // Set library track's lastModified into the future
        Track existing = Track.builder()
                .id("past-id-1")
                .filePath(file.toAbsolutePath().normalize().toString())
                .title("Old Title")
                .artist("Old Artist")
                .rating(4)
                .playCount(15)
                .dateAdded(now.minusSeconds(1000))
                .lastModified(now.plusSeconds(3600)) // Far in future compared to actual disk file
                .bpm(0)
                .build();
        library.addTrack(existing);

        AtomicInteger extractCount = new AtomicInteger(0);
        MetadataExtractor extractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                extractCount.incrementAndGet();
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title("Refreshed Title")
                        .artist("Refreshed Artist")
                        .bpm(130)
                        .bitrate(320)
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                extractor,
                10
        );

        // 1. Normal scanAndImportAsync (forceMetadataRefresh = false): should SKIP because file is older than library lastModified
        ImportTask normalTask = service.scanAndImportAsync(file, null);
        ImportResult normalResult = normalTask.getResultFuture().get(5, TimeUnit.SECONDS);
        assertThat(normalResult.skippedCount()).isEqualTo(1);
        assertThat(normalResult.updatedCount()).isEqualTo(0);
        assertThat(extractCount.get()).isEqualTo(0); // Extraction was skipped!

        // 2. rescanAndImportAsync (forceMetadataRefresh = true): must FORCE re-extraction and update
        ImportTask rescanTask = service.rescanAndImportAsync(file, null);
        ImportResult rescanResult = rescanTask.getResultFuture().get(5, TimeUnit.SECONDS);
        assertThat(rescanResult.updatedCount()).isEqualTo(1);
        assertThat(rescanResult.skippedCount()).isEqualTo(0);
        assertThat(extractCount.get()).isEqualTo(1); // Forced extraction executed!

        Track updated = library.getTrack("past-id-1");
        assertThat(updated.getTitle()).isEqualTo("Refreshed Title");
        assertThat(updated.getBpm()).isEqualTo(130);
        assertThat(updated.getBitrate()).isEqualTo(320);
        assertThat(updated.getRating()).isEqualTo(4); // Preserved
        assertThat(updated.getPlayCount()).isEqualTo(15); // Preserved
    }

    @Test
    @DisplayName("rescanAndImportAsync collection overload rescans multiple paths and imports new tracks")
    void testRescanMultiplePathsCollectionOverload() throws Exception {
        Library library = new Library();

        Path dir1 = Files.createDirectory(tempDir.resolve("dir1"));
        Path dir2 = Files.createDirectory(tempDir.resolve("dir2"));

        Path file1 = Files.createFile(dir1.resolve("song1.mp3"));
        Path file2 = Files.createFile(dir2.resolve("song2.mp3"));

        // Register file1 in library with 0 BPM
        library.addTrack(Track.builder()
                .id("id-dir1")
                .filePath(file1.toAbsolutePath().normalize().toString())
                .title("Song 1")
                .bpm(0)
                .build());

        MetadataExtractor extractor = new MetadataExtractor() {
            @Override
            public Track extract(Path filePath) {
                return Track.builder()
                        .filePath(filePath.toAbsolutePath().normalize().toString())
                        .title(filePath.getFileName().toString())
                        .bpm(125)
                        .build();
            }

            @Override
            public boolean supports(Path filePath) {
                return true;
            }
        };

        LibraryImportService service = new LibraryImportService(
                library,
                new TestStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                extractor,
                10
        );

        ImportTask task = service.rescanAndImportAsync(List.of(dir1, dir2), null);
        ImportResult result = task.getResultFuture().get(5, TimeUnit.SECONDS);

        assertThat(result.updatedCount()).isEqualTo(1); // song1 updated
        assertThat(result.addedCount()).isEqualTo(1);   // song2 added
        assertThat(library.getTrackCount()).isEqualTo(2);

        Track updatedSong1 = library.getTrack("id-dir1");
        assertThat(updatedSong1.getBpm()).isEqualTo(125);
    }

    // --- Test Doubles ---

    private static class TestStorage implements LibraryStorage {
        boolean saved = false;

        @Override
        public void save(Library library, Path targetPath) {
            saved = true;
        }

        @Override
        public Library load(Path sourcePath) {
            return new Library();
        }
    }

    private static class DummyMetadataExtractor implements MetadataExtractor {
        @Override
        public Track extract(Path filePath) {
            return Track.builder()
                    .filePath(filePath.toAbsolutePath().normalize().toString())
                    .title(filePath.getFileName().toString())
                    .build();
        }

        @Override
        public boolean supports(Path filePath) {
            return true;
        }
    }

    private static class SlowMetadataExtractor implements MetadataExtractor {
        @Override
        public Track extract(Path filePath) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {}
            return Track.builder()
                    .filePath(filePath.toAbsolutePath().normalize().toString())
                    .title(filePath.getFileName().toString())
                    .build();
        }

        @Override
        public boolean supports(Path filePath) {
            return true;
        }
    }
}
