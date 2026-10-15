package com.musiclibrary;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Account;
import com.musiclibrary.model.Library;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;
import com.musiclibrary.service.importer.FileScanner;
import com.musiclibrary.service.importer.LibraryImportService;
import com.musiclibrary.service.metadata.Mp3MetadataExtractor;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import com.musiclibrary.service.storage.JsonToSqliteMigrator;
import com.musiclibrary.service.storage.LibraryStorage;
import com.musiclibrary.service.storage.SqliteLibraryStorage;
import com.musiclibrary.settings.SettingsManager;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main application entry point for Music Library Manager.
 * Initializes configuration, themes, i18n, loads persisted library from SQLite
 * (migrating from legacy JSON if needed), wires services, and presents the main GUI on the EDT.
 */
public class Main {

    private static final System.Logger LOGGER = System.getLogger(Main.class.getName());

    public static final String STORAGE_PATH_PROPERTY = "musiclibrary.storage.path";
    public static final String DB_PATH_PROPERTY = "musiclibrary.db.path";

    public static final Path DEFAULT_LIBRARY_PATH = Paths.get(
            System.getProperty("user.home"),
            ".musiclibrary",
            "library.json"
    );

    private static volatile Thread activeShutdownHook = null;

    /**
     * Resolves the effective storage path, respecting system property override if set.
     */
    public static Path getStoragePath() {
        String customPath = System.getProperty(STORAGE_PATH_PROPERTY);
        if (customPath != null && !customPath.isBlank()) {
            return Paths.get(customPath);
        }
        return DEFAULT_LIBRARY_PATH;
    }

    /**
     * Resolves the effective SQLite database path, respecting system property override if set.
     */
    public static String getDatabasePath() {
        String customPath = System.getProperty(DB_PATH_PROPERTY);
        if (customPath != null && !customPath.isBlank()) {
            return customPath;
        }
        return Paths.get(
                System.getProperty("user.home"),
                ".musiclibrary",
                "library.db"
        ).toString();
    }

    public static void main(String[] args) {
        // Headless environment guard (CI, test runners, servers)
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Running in headless environment. GUI startup skipped.");
            return;
        }

        Path storagePath = getStoragePath();
        SwingUtilities.invokeLater(() -> launchApplication(storagePath));
    }

    /**
     * Launches the full application window with the specified storage file path.
     * Initializes SQLite connection and migrates JSON if present.
     * Must be invoked on the Swing EDT.
     *
     * @param storagePath path to legacy library.json persistence file (for migration)
     * @return the initialized MainFrame
     */
    public static MainFrame launchApplication(Path storagePath) {
        DatabaseManager dbManager = new DatabaseManager(getDatabasePath());
        return launchApplication(dbManager, storagePath);
    }

    /**
     * Launches the full application window with an explicitly provided DatabaseManager.
     *
     * @param dbManager   SQLite DatabaseManager instance
     * @param storagePath path to legacy library.json persistence file (for migration)
     * @return the initialized MainFrame
     */
    public static MainFrame launchApplication(DatabaseManager dbManager, Path storagePath) {
        // 0. Load preferences & apply Theme and Language early
        SettingsManager settingsManager = SettingsManager.getInstance();
        settingsManager.load();
        settingsManager.applyThemeAndLanguageEarly();

        // 1. Initialize Themes and Internationalization
        ThemeManager.getInstance().init();
        I18nManager.getInstance().init();

        // 2. Initialize Database schema
        try {
            SchemaInitializer.initialize(dbManager);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to initialize SQLite database schema: " + e.getMessage(), e);
        }

        // 3. Migrate from JSON if needed
        if (storagePath != null && Files.exists(storagePath)) {
            try {
                JsonToSqliteMigrator migrator = new JsonToSqliteMigrator(dbManager);
                migrator.migrateIfNeeded(storagePath, Account.DEFAULT_ACCOUNT_ID);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Migration from JSON to SQLite failed: " + e.getMessage(), e);
            }
        }

        // 4. Load or initialize music library from SQLite
        SqliteLibraryStorage storage = new SqliteLibraryStorage(dbManager, Account.DEFAULT_ACCOUNT_ID);
        Library library = loadLibrarySafe(storage, storagePath);

        // 5. Initialize background import service
        LibraryImportService importService = new LibraryImportService(
                library,
                storage,
                storagePath,
                new FileScanner(),
                new Mp3MetadataExtractor(),
                LibraryImportService.DEFAULT_BATCH_SIZE
        );

        // 6. Initialize main window with explicit storagePath and dbManager
        MainFrame mainFrame = new MainFrame(library, importService, null, null, null, storagePath, dbManager);
        mainFrame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        // 7. Automatic persistence on shutdown or window close
        AtomicBoolean saved = new AtomicBoolean(false);
        Runnable saveAction = () -> {
            if (saved.compareAndSet(false, true)) {
                try {
                    if (mainFrame.getAudioPlayer() != null) {
                        mainFrame.getAudioPlayer().stop();
                    }
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.WARNING, "Error stopping audio during shutdown save: " + ex.getMessage(), ex);
                }
                try {
                    settingsManager.saveFrom(mainFrame);
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.WARNING, "Error saving user preferences during shutdown: " + ex.getMessage(), ex);
                }
                try {
                    storage.save(library, storagePath);
                } catch (IOException e) {
                    LOGGER.log(System.Logger.Level.ERROR, "Failed to persist library during shutdown: " + e.getMessage(), e);
                } finally {
                    try {
                        dbManager.close();
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.ERROR, "Failed to close database during shutdown: " + e.getMessage(), e);
                    }
                }
            }
        };

        // Clear any prior hook and register managed shutdown hook
        removeShutdownHook();
        Thread hook = new Thread(saveAction, "Library-ShutdownSaveHook");
        activeShutdownHook = hook;
        try {
            Runtime.getRuntime().addShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 1. Immediately stop audio playback so sound stops instantaneously on window close
                try {
                    if (mainFrame.getAudioPlayer() != null) {
                        mainFrame.getAudioPlayer().stop();
                    }
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.WARNING, "Error stopping audio on window close: " + ex.getMessage(), ex);
                }

                // 2. Capture user preferences synchronously on EDT before closing
                try {
                    settingsManager.saveFrom(mainFrame);
                } catch (Exception ex) {
                    LOGGER.log(System.Logger.Level.WARNING, "Error saving user preferences on window close: " + ex.getMessage(), ex);
                }

                // 3. Immediately hide window from user screen
                mainFrame.setVisible(false);
                removeShutdownHook();

                // 4. Run save asynchronously on background thread, then clean up and exit
                Thread shutdownWorker = new Thread(() -> {
                    try {
                        saveAction.run();
                    } finally {
                        SwingUtilities.invokeLater(mainFrame::dispose);
                        System.exit(0);
                    }
                }, "App-ShutdownWorker");
                shutdownWorker.setDaemon(false);
                shutdownWorker.start();
            }
        });

        // 8. Restore user preferences (window bounds, volume, table state) and display window
        settingsManager.applyTo(mainFrame);
        mainFrame.setVisible(true);

        return mainFrame;
    }

    /**
     * Safely unregisters the active shutdown hook if present.
     */
    public static synchronized void removeShutdownHook() {
        if (activeShutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(activeShutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown already in progress
            }
            activeShutdownHook = null;
        }
    }

    /**
     * Safely loads library from storage or returns a fresh Library on error.
     */
    public static Library loadLibrarySafe(LibraryStorage storage, Path storagePath) {
        if (storage != null) {
            try {
                return storage.load(storagePath);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not load existing library, initializing empty library: " + e.getMessage(), e);
            }
        }
        return new Library();
    }

    /**
     * Backward-compatible overload for JsonLibraryStorage.
     */
    public static Library loadLibrarySafe(JsonLibraryStorage storage, Path storagePath) {
        return loadLibrarySafe((LibraryStorage) storage, storagePath);
    }
}
