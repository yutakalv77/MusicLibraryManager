package com.musiclibrary.service.importer;

import com.musiclibrary.model.Library;
import com.musiclibrary.service.metadata.MetadataExtractor;
import com.musiclibrary.service.storage.LibraryStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderWatcherServiceTest {

    @TempDir
    Path tempDir;

    private String originalStorageProperty;

    @BeforeEach
    void setUpStorageProperty() {
        originalStorageProperty = System.getProperty("musiclibrary.storage.path");
        System.setProperty("musiclibrary.storage.path", tempDir.resolve("isolated_watcher_storage.json").toString());
    }

    @AfterEach
    void restoreStorageProperty() {
        if (originalStorageProperty != null) {
            System.setProperty("musiclibrary.storage.path", originalStorageProperty);
        } else {
            System.clearProperty("musiclibrary.storage.path");
        }
    }

    private LibraryImportService createDummyImportService(Library library) {
        return new LibraryImportService(
                library != null ? library : new Library(),
                new DummyStorage(),
                tempDir.resolve("watcher_dummy_storage.json"),
                new FileScanner(),
                new DummyExtractor(),
                10
        );
    }

    @Test
    @DisplayName("Lifecycle management: start, isRunning, getWatchedFolder, stop")
    void testLifecycle() throws IOException {
        Library library = new Library();
        LibraryImportService importService = createDummyImportService(library);
        FolderWatcherService watcher = new FolderWatcherService(importService);

        assertThat(watcher.isRunning()).isFalse();
        assertThat(watcher.getWatchedFolder()).isNull();

        watcher.start(tempDir);
        assertThat(watcher.isRunning()).isTrue();
        assertThat(watcher.getWatchedFolder()).isEqualTo(tempDir.toAbsolutePath().normalize());

        watcher.stop();
        assertThat(watcher.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Constructor throws NullPointerException when importService is null")
    void testConstructorNullSafety() {
        assertThatThrownBy(() -> new FolderWatcherService(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("importService cannot be null");
    }

    @Test
    @DisplayName("Multiple start and stop invocations handle state transitions cleanly")
    void testMultipleStartAndStop() throws IOException {
        Library library = new Library();
        FolderWatcherService watcher = new FolderWatcherService(createDummyImportService(library));

        // stop when not running should not throw
        assertThatCode(watcher::stop).doesNotThrowAnyException();
        assertThat(watcher.isRunning()).isFalse();

        // start first folder
        watcher.start(tempDir);
        assertThat(watcher.isRunning()).isTrue();
        assertThat(watcher.getWatchedFolder()).isEqualTo(tempDir.toAbsolutePath().normalize());

        // start second folder (re-registers and stops previous)
        Path subDir = tempDir.resolve("sub_folder");
        Files.createDirectories(subDir);
        watcher.start(subDir);
        assertThat(watcher.isRunning()).isTrue();
        assertThat(watcher.getWatchedFolder()).isEqualTo(subDir.toAbsolutePath().normalize());

        // consecutive stop calls
        watcher.stop();
        assertThat(watcher.isRunning()).isFalse();
        watcher.stop();
        assertThat(watcher.isRunning()).isFalse();
    }

    @Test
    @DisplayName("start throws IllegalArgumentException for non-directory or null path")
    void testStartValidation() throws IOException {
        Library library = new Library();
        FolderWatcherService watcher = new FolderWatcherService(createDummyImportService(library));

        assertThatThrownBy(() -> watcher.start(null))
                .isInstanceOf(IllegalArgumentException.class);

        Path nonExistent = tempDir.resolve("not_found");
        assertThatThrownBy(() -> watcher.start(nonExistent))
                .isInstanceOf(IllegalArgumentException.class);

        Path regularFile = tempDir.resolve("file.txt");
        Files.writeString(regularFile, "hello");
        assertThatThrownBy(() -> watcher.start(regularFile))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FolderWatcher detects newly created audio file and triggers stability check and import")
    void testNewFileDetection() throws Exception {
        Library library = new Library();
        LibraryImportService importService = new LibraryImportService(
                library,
                new DummyStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                new DummyExtractor(),
                10
        );

        FolderWatcherService watcher = new FolderWatcherService(importService);
        watcher.setStabilityTimeoutMs(1500);
        watcher.setStabilityCheckIntervalMs(100);

        CountDownLatch detectedLatch = new CountDownLatch(1);
        AtomicBoolean fileDetected = new AtomicBoolean(false);

        watcher.setOnFileDetectedCallback(path -> {
            if (path.getFileName().toString().endsWith(".mp3")) {
                fileDetected.set(true);
                detectedLatch.countDown();
            }
        });

        watcher.start(tempDir);

        try {
            // Create a new MP3 file in the watched folder
            Path newSong = tempDir.resolve("newly_added.mp3");
            Files.writeString(newSong, "fake audio bytes");

            boolean detected = detectedLatch.await(5, TimeUnit.SECONDS);
            assertThat(detected).isTrue();
            assertThat(fileDetected.get()).isTrue();

            // Wait brief moment for background import to complete
            Thread.sleep(500);
            assertThat(library.getTrackCount()).isEqualTo(1);
        } finally {
            watcher.stop();
        }
    }

    @Test
    @DisplayName("FolderWatcher ignores newly created non-audio files (txt, jpg, log)")
    void testNonAudioFileIgnored() throws Exception {
        Library library = new Library();
        LibraryImportService importService = new LibraryImportService(
                library,
                new DummyStorage(),
                tempDir.resolve("lib.json"),
                new FileScanner(),
                new DummyExtractor(),
                10
        );

        FolderWatcherService watcher = new FolderWatcherService(importService);
        watcher.setStabilityTimeoutMs(1000);
        watcher.setStabilityCheckIntervalMs(50);

        AtomicBoolean nonAudioDetected = new AtomicBoolean(false);
        watcher.setOnFileDetectedCallback(path -> nonAudioDetected.set(true));

        watcher.start(tempDir);

        try {
            // Create non-audio files
            Files.writeString(tempDir.resolve("notes.txt"), "some notes");
            Files.writeString(tempDir.resolve("cover.jpg"), "fake jpg");
            Files.writeString(tempDir.resolve("debug.log"), "log text");

            // Allow brief time for watcher loop
            Thread.sleep(600);

            assertThat(nonAudioDetected.get()).isFalse();
            assertThat(library.getTrackCount()).isEqualTo(0);
        } finally {
            watcher.stop();
        }
    }

    @Test
    @DisplayName("waitForFileStability returns true when file size is stable")
    void testFileStabilityCheck() throws IOException {
        Path file = tempDir.resolve("stable.mp3");
        Files.writeString(file, "content");

        FolderWatcherService watcher = new FolderWatcherService(createDummyImportService(new Library()));
        watcher.start(tempDir);

        try {
            boolean stable = watcher.waitForFileStability(file);
            assertThat(stable).isTrue();
        } finally {
            watcher.stop();
        }
    }

    @Test
    @DisplayName("waitForFileStability returns false when file does not exist or watcher is stopped")
    void testFileStabilityEdgeCases() throws IOException {
        FolderWatcherService watcher = new FolderWatcherService(createDummyImportService(new Library()));
        watcher.setStabilityTimeoutMs(500);
        watcher.setStabilityCheckIntervalMs(50);

        // Not running
        Path nonExistent = tempDir.resolve("ghost.mp3");
        assertThat(watcher.waitForFileStability(nonExistent)).isFalse();

        // Running but file doesn't exist
        watcher.start(tempDir);
        try {
            assertThat(watcher.waitForFileStability(nonExistent)).isFalse();
        } finally {
            watcher.stop();
        }
    }

    // --- Dummy Doubles ---

    private static class DummyStorage implements LibraryStorage {
        @Override
        public void save(Library library, Path targetPath) {}
        @Override
        public Library load(Path sourcePath) { return new Library(); }
    }

    private static class DummyExtractor implements MetadataExtractor {
        @Override
        public com.musiclibrary.model.Track extract(Path filePath) {
            return com.musiclibrary.model.Track.builder()
                    .filePath(filePath.toAbsolutePath().normalize().toString())
                    .title("Detected Track")
                    .build();
        }
        @Override
        public boolean supports(Path filePath) { return true; }
    }
}
