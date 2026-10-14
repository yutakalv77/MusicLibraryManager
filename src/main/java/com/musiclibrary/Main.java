package com.musiclibrary;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Library;
import com.musiclibrary.service.importer.FileScanner;
import com.musiclibrary.service.importer.LibraryImportService;
import com.musiclibrary.service.metadata.Mp3MetadataExtractor;
import com.musiclibrary.service.storage.JsonLibraryStorage;
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
 * Initializes configuration, themes, i18n, loads persisted library,
 * wires services, and presents the main GUI on the Event Dispatch Thread (EDT).
 */
public class Main {

    public static final String STORAGE_PATH_PROPERTY = "musiclibrary.storage.path";

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
     * Must be invoked on the Swing EDT.
     *
     * @param storagePath path to library.json persistence file
     */
    public static MainFrame launchApplication(Path storagePath) {
        // 1. Initialize Themes and Internationalization
        ThemeManager.getInstance().init();
        I18nManager.getInstance().init();

        // 2. Load or initialize music library
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library library = loadLibrarySafe(storage, storagePath);

        // 3. Initialize background import service
        LibraryImportService importService = new LibraryImportService(
                library,
                storage,
                storagePath,
                new FileScanner(),
                new Mp3MetadataExtractor(),
                LibraryImportService.DEFAULT_BATCH_SIZE
        );

        // 4. Initialize main window with explicit storagePath
        MainFrame mainFrame = new MainFrame(library, importService, storagePath);
        mainFrame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);

        // 5. Automatic persistence on shutdown or window close
        AtomicBoolean saved = new AtomicBoolean(false);
        Runnable saveAction = () -> {
            if (saved.compareAndSet(false, true)) {
                try {
                    storage.save(library, storagePath);
                } catch (IOException e) {
                    System.err.println("Failed to persist library during shutdown: " + e.getMessage());
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
                // Immediately hide window from user screen
                mainFrame.setVisible(false);
                removeShutdownHook();

                // Run save asynchronously on background thread, then clean up and exit
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

        // 6. Center and display window
        mainFrame.setLocationRelativeTo(null);
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
     * Safely loads library from disk or returns a fresh Library if not present or on error.
     */
    public static Library loadLibrarySafe(JsonLibraryStorage storage, Path storagePath) {
        if (storagePath != null && Files.exists(storagePath)) {
            try {
                return storage.load(storagePath);
            } catch (Exception e) {
                System.err.println("Warning: Could not load existing library, initializing empty library: " + e.getMessage());
            }
        }
        return new Library();
    }
}
