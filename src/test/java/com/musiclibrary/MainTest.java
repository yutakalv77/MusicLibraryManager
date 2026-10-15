package com.musiclibrary;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.SqliteTrackRepository;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import com.musiclibrary.service.storage.SqliteLibraryStorage;
import com.musiclibrary.ui.MainFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    @DisplayName("getDatabasePath respects system property override and has valid default")
    void testGetDatabasePath() {
        String originalProperty = System.getProperty(Main.DB_PATH_PROPERTY);
        try {
            String customDb = tempDir.resolve("custom.db").toString();
            System.setProperty(Main.DB_PATH_PROPERTY, customDb);
            assertThat(Main.getDatabasePath()).isEqualTo(customDb);

            // Clear property to verify default path resolution in user home
            System.clearProperty(Main.DB_PATH_PROPERTY);
            assertThat(Main.getDatabasePath()).isNotBlank();
            assertThat(Main.getDatabasePath()).contains(".musiclibrary");
        } finally {
            if (originalProperty != null) {
                System.setProperty(Main.DB_PATH_PROPERTY, originalProperty);
            } else {
                System.clearProperty(Main.DB_PATH_PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("loadLibrarySafe works seamlessly with SqliteLibraryStorage and handles errors gracefully")
    void testLoadLibrarySafeWithSqliteStorage() throws SQLException, IOException {
        DatabaseManager db = new DatabaseManager(":memory:");
        SqliteLibraryStorage storage = new SqliteLibraryStorage(db, Account.DEFAULT_ACCOUNT_ID);

        // 1. Storage null -> empty library
        assertThat(Main.loadLibrarySafe((SqliteLibraryStorage) null, null)).isNotNull();
        assertThat(Main.loadLibrarySafe((SqliteLibraryStorage) null, null).getTrackCount()).isEqualTo(0);

        // 2. Initial empty DB -> empty library
        Library initial = Main.loadLibrarySafe(storage, null);
        assertThat(initial).isNotNull();
        assertThat(initial.getTrackCount()).isEqualTo(0);

        // 3. Populated DB -> loaded library
        storage.save(new Library(List.of(Track.builder().filePath("C:/m.mp3").title("M").build()), List.of()), null);
        Library populated = Main.loadLibrarySafe(storage, null);
        assertThat(populated.getTrackCount()).isEqualTo(1);

        // 4. Closed DB manager -> returns fallback empty library instead of throwing
        db.close();
        Library fallback = Main.loadLibrarySafe(storage, null);
        assertThat(fallback).isNotNull();
        assertThat(fallback.getTrackCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("launchApplication automatically migrates legacy JSON and boots up GUI with SQLite")
    void testLaunchApplicationWithLegacyJsonMigration() throws IOException, InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return; // Skip on headless environments
        }

        Path legacyJson = tempDir.resolve("legacy_to_migrate.json");
        Track t1 = Track.builder().filePath("C:/Music/LegacyTrack.mp3").title("Legacy Song").build();
        Playlist pl = new Playlist("pl-legacy", "Legacy PL", List.of(t1.getId()));
        JsonLibraryStorage jsonStorage = new JsonLibraryStorage();
        jsonStorage.save(new Library(List.of(t1), List.of(pl)), legacyJson);
        assertThat(Files.exists(legacyJson)).isTrue();

        DatabaseManager db = new DatabaseManager(":memory:");
        AtomicReference<MainFrame> frameRef = new AtomicReference<>();

        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = Main.launchApplication(db, legacyJson);
                frameRef.set(frame);
            });

            MainFrame frame = frameRef.get();
            assertThat(frame).isNotNull();
            assertThat(frame.isVisible()).isTrue();

            // Original JSON file was migrated and backed up
            assertThat(Files.exists(legacyJson)).isFalse();

            // Track exists in SQLite
            SqliteTrackRepository trackRepo = new SqliteTrackRepository(db);
            assertThat(trackRepo.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).hasSize(1);
            assertThat(trackRepo.findByAccountId(Account.DEFAULT_ACCOUNT_ID).get(0).getTitle()).isEqualTo("Legacy Song");

        } finally {
            Main.removeShutdownHook();
            if (frameRef.get() != null) {
                SwingUtilities.invokeAndWait(() -> frameRef.get().dispose());
            }
            try {
                db.close();
            } catch (SQLException ignored) {
            }
        }
    }
}


