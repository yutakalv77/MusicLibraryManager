package com.musiclibrary.service.importer;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Background folder watcher service using Java NIO WatchService.
 * Monitors a designated folder for newly added audio files (ENTRY_CREATE),
 * waits for file write completion (stability check), and performs silent incremental import.
 */
public class FolderWatcherService {

    private static final long DEFAULT_STABILITY_TIMEOUT_MS = 5000;
    private static final long DEFAULT_STABILITY_CHECK_INTERVAL_MS = 300;

    private final LibraryImportService importService;
    private final FileScanner fileScanner;
    private ExecutorService workerPool;

    private WatchService watchService;
    private Thread watcherThread;
    private Path watchedFolder;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Consumer<Path> onFileDetectedCallback;
    private long stabilityTimeoutMs = DEFAULT_STABILITY_TIMEOUT_MS;
    private long stabilityCheckIntervalMs = DEFAULT_STABILITY_CHECK_INTERVAL_MS;

    /**
     * Constructs a FolderWatcherService attached to the given import service.
     *
     * @param importService service for executing incremental import
     */
    public FolderWatcherService(LibraryImportService importService) {
        this(importService, new FileScanner());
    }

    /**
     * Constructs a FolderWatcherService with custom FileScanner.
     *
     * @param importService service for executing incremental import
     * @param fileScanner   file scanner for extension validation
     */
    public FolderWatcherService(LibraryImportService importService, FileScanner fileScanner) {
        this.importService = Objects.requireNonNull(importService, "importService cannot be null");
        this.fileScanner = fileScanner != null ? fileScanner : new FileScanner();
        this.workerPool = createWorkerPool();
    }

    private static ExecutorService createWorkerPool() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FolderWatcher-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts monitoring the specified folder for new audio files.
     *
     * @param folder directory to watch
     * @throws IOException if WatchService cannot register the directory
     */
    public synchronized void start(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Target path must be an existing directory: " + folder);
        }

        stop();

        if (workerPool == null || workerPool.isShutdown()) {
            workerPool = createWorkerPool();
        }

        this.watchedFolder = folder.toAbsolutePath().normalize();
        this.watchService = FileSystems.getDefault().newWatchService();
        this.watchedFolder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        this.running.set(true);

        this.watcherThread = new Thread(this::watchLoop, "FolderWatcher-EventLoop");
        this.watcherThread.setDaemon(true);
        this.watcherThread.start();
    }

    /**
     * Stops the watcher thread and releases WatchService resources and worker threads.
     */
    public synchronized void stop() {
        if (workerPool != null && !workerPool.isShutdown()) {
            workerPool.shutdown();
        }

        if (!running.get()) {
            return;
        }
        running.set(false);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }

        if (watcherThread != null) {
            watcherThread.interrupt();
            watcherThread = null;
        }
    }

    /**
     * Checks if the watcher is actively running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the currently watched folder path, or null if stopped.
     *
     * @return watched directory path
     */
    public Path getWatchedFolder() {
        return watchedFolder;
    }

    private void watchLoop() {
        while (running.get() && watchService != null) {
            WatchKey key;
            try {
                key = watchService.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                break;
            }

            if (key == null) {
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (!running.get()) {
                    break;
                }

                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path child = pathEvent.context();
                    Path fullPath = watchedFolder.resolve(child);

                    if (fileScanner.isSupported(fullPath)) {
                        workerPool.submit(() -> handleNewFile(fullPath));
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }

    private void handleNewFile(Path file) {
        if (onFileDetectedCallback != null) {
            onFileDetectedCallback.accept(file);
        }

        // Wait until download/copy is completed and file size is stable
        boolean stable = waitForFileStability(file);
        if (stable && running.get() && Files.exists(file)) {
            // Trigger silent incremental import
            importService.scanAndImportAsync(file, null);
        }
    }

    /**
     * Waits until the file size remains non-zero and unchanging across checks,
     * indicating that file copy/download has finished.
     */
    boolean waitForFileStability(Path file) {
        long startTime = System.currentTimeMillis();
        long lastSize = -1;

        while (System.currentTimeMillis() - startTime < stabilityTimeoutMs) {
            if (!running.get() || !Files.exists(file)) {
                return false;
            }

            try {
                long currentSize = Files.size(file);
                if (currentSize > 0 && currentSize == lastSize) {
                    return true;
                }
                lastSize = currentSize;
            } catch (IOException e) {
                // File locked by writer; continue waiting
            }

            try {
                Thread.sleep(stabilityCheckIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return Files.exists(file);
    }

    // --- Configuration & Callbacks ---

    public void setOnFileDetectedCallback(Consumer<Path> onFileDetectedCallback) {
        this.onFileDetectedCallback = onFileDetectedCallback;
    }

    public void setStabilityTimeoutMs(long stabilityTimeoutMs) {
        this.stabilityTimeoutMs = stabilityTimeoutMs;
    }

    public void setStabilityCheckIntervalMs(long stabilityCheckIntervalMs) {
        this.stabilityCheckIntervalMs = stabilityCheckIntervalMs;
    }
}
