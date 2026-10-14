package com.musiclibrary;

import com.musiclibrary.model.Library;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Main.main does not throw exceptions when executed")
    void testMainInvocation() {
        String originalProperty = System.getProperty(Main.STORAGE_PATH_PROPERTY);
        Path testStorage = tempDir.resolve("test_main_library.json");
        System.setProperty(Main.STORAGE_PATH_PROPERTY, testStorage.toString());
        try {
            assertThatCode(() -> Main.main(new String[0])).doesNotThrowAnyException();
        } finally {
            Main.removeShutdownHook();
            if (originalProperty != null) {
                System.setProperty(Main.STORAGE_PATH_PROPERTY, originalProperty);
            } else {
                System.clearProperty(Main.STORAGE_PATH_PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("getStoragePath respects system property override")
    void testGetStoragePathOverride() {
        String originalProperty = System.getProperty(Main.STORAGE_PATH_PROPERTY);
        Path customPath = tempDir.resolve("custom_storage.json");
        System.setProperty(Main.STORAGE_PATH_PROPERTY, customPath.toString());
        try {
            assertThat(Main.getStoragePath()).isEqualTo(customPath);
        } finally {
            if (originalProperty != null) {
                System.setProperty(Main.STORAGE_PATH_PROPERTY, originalProperty);
            } else {
                System.clearProperty(Main.STORAGE_PATH_PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("DEFAULT_LIBRARY_PATH is configured in user home directory")
    void testDefaultLibraryPath() {
        assertThat(Main.DEFAULT_LIBRARY_PATH).isNotNull();
        assertThat(Main.DEFAULT_LIBRARY_PATH.toString()).contains(".musiclibrary");
        assertThat(Main.DEFAULT_LIBRARY_PATH.toString()).endsWith("library.json");
    }

    @Test
    @DisplayName("loadLibrarySafe loads existing library or returns empty library on missing or corrupted file")
    void testLoadLibrarySafe() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();

        // 1. Missing file -> returns new empty library
        Path missingPath = tempDir.resolve("non_existent.json");
        Library emptyLib = Main.loadLibrarySafe(storage, missingPath);
        assertThat(emptyLib).isNotNull();
        assertThat(emptyLib.getTrackCount()).isEqualTo(0);

        // 2. Corrupted file -> returns new empty library without crashing
        Path corruptPath = tempDir.resolve("corrupt.json");
        Files.writeString(corruptPath, "{ invalid json content !!!");
        Library fallbackLib = Main.loadLibrarySafe(storage, corruptPath);
        assertThat(fallbackLib).isNotNull();
        assertThat(fallbackLib.getTrackCount()).isEqualTo(0);

        // 3. Valid file -> successfully loads populated library
        Path validPath = tempDir.resolve("valid.json");
        Library original = new Library();
        original.addTrack(Track.builder().filePath("C:/music/test.mp3").title("Valid Song").build());
        storage.save(original, validPath);

        Library loaded = Main.loadLibrarySafe(storage, validPath);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getTrackCount()).isEqualTo(1);
        assertThat(loaded.findTracks("Valid Song")).hasSize(1);
    }
}
