package com.musiclibrary.service.importer;

import com.musiclibrary.model.Library;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.metadata.MetadataExtractor;
import com.musiclibrary.service.metadata.Mp3MetadataExtractor;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import com.musiclibrary.service.storage.LibraryStorage;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating asynchronous audio scanning, metadata extraction,
 * batch library registration, deduplication, and persistence.
 * Protects the Swing EDT by performing all heavy I/O off-thread while
 * delivering progress callbacks on the EDT.
 */
public class LibraryImportService {

    public static final int DEFAULT_BATCH_SIZE = 250;

    public static Path getDefaultStoragePath() {
        String customPath = System.getProperty("musiclibrary.storage.path");
        if (customPath != null && !customPath.isBlank()) {
            return Paths.get(customPath);
        }
        return Paths.get(
                System.getProperty("user.home"),
                ".musiclibrary",
                "library.json"
        );
    }

    private final Library library;
    private final LibraryStorage storage;
    private final Path storagePath;
    private final FileScanner fileScanner;
    private final MetadataExtractor metadataExtractor;
    private final ExecutorService executorService;
    private final int batchSize;
    private DuplicatePolicy duplicatePolicy;

    /**
     * Default constructor wiring default storage and extractors.
     *
     * @param library target music library
     */
    public LibraryImportService(Library library) {
        this(library, new JsonLibraryStorage(), getDefaultStoragePath(), new FileScanner(), new Mp3MetadataExtractor(), DEFAULT_BATCH_SIZE, DuplicatePolicy.SKIP_SAME_CONTENT);
    }

    /**
     * Constructor allowing customization of duplicate policy.
     *
     * @param library         target music library
     * @param duplicatePolicy policy for handling content duplicates
     */
    public LibraryImportService(Library library, DuplicatePolicy duplicatePolicy) {
        this(library, new JsonLibraryStorage(), getDefaultStoragePath(), new FileScanner(), new Mp3MetadataExtractor(), DEFAULT_BATCH_SIZE, duplicatePolicy);
    }

    /**
     * Constructor allowing custom storage path.
     *
     * @param library     target music library
     * @param storagePath explicit storage path
     */
    public LibraryImportService(Library library, Path storagePath) {
        this(library, new JsonLibraryStorage(), (storagePath != null ? storagePath : getDefaultStoragePath()), new FileScanner(), new Mp3MetadataExtractor(), DEFAULT_BATCH_SIZE, DuplicatePolicy.SKIP_SAME_CONTENT);
    }

    /**
     * Configurable constructor for testing and DI with default SKIP_SAME_CONTENT policy.
     *
     * @param library           target library
     * @param storage           storage engine for saving changes
     * @param storagePath       path to library.json
     * @param fileScanner       file scanner engine
     * @param metadataExtractor tag extractor engine
     * @param batchSize         batch commit size (default 50)
     */
    public LibraryImportService(Library library,
                                LibraryStorage storage,
                                Path storagePath,
                                FileScanner fileScanner,
                                MetadataExtractor metadataExtractor,
                                int batchSize) {
        this(library, storage, storagePath, fileScanner, metadataExtractor, batchSize, DuplicatePolicy.SKIP_SAME_CONTENT);
    }

    /**
     * Fully configurable constructor for testing and DI.
     *
     * @param library           target library
     * @param storage           storage engine for saving changes
     * @param storagePath       path to library.json
     * @param fileScanner       file scanner engine
     * @param metadataExtractor tag extractor engine
     * @param batchSize         batch commit size (default 50)
     * @param duplicatePolicy   duplicate policy for handling identical content
     */
    public LibraryImportService(Library library,
                                LibraryStorage storage,
                                Path storagePath,
                                FileScanner fileScanner,
                                MetadataExtractor metadataExtractor,
                                int batchSize,
                                DuplicatePolicy duplicatePolicy) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.storage = storage;
        this.storagePath = storagePath;
        this.fileScanner = fileScanner != null ? fileScanner : new FileScanner();
        this.metadataExtractor = metadataExtractor != null ? metadataExtractor : new Mp3MetadataExtractor();
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.duplicatePolicy = duplicatePolicy != null ? duplicatePolicy : DuplicatePolicy.SKIP_SAME_CONTENT;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "LibraryImporter-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    public DuplicatePolicy getDuplicatePolicy() {
        return duplicatePolicy;
    }

    public void setDuplicatePolicy(DuplicatePolicy duplicatePolicy) {
        this.duplicatePolicy = Objects.requireNonNull(duplicatePolicy, "duplicatePolicy cannot be null");
    }

    /**
     * Initiates an orderly shutdown of the importer worker thread pool.
     */
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks and halts the worker thread pool immediately.
     *
     * @return list of tasks that never commenced execution
     */
    public List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    /**
     * Checks if the importer worker thread pool has been shut down.
     *
     * @return true if shut down, false otherwise
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    /**
     * Asynchronously scans the specified folder or file and imports new/modified tracks into the library.
     *
     * @param rootPath directory or audio file path to import
     * @param listener progress reporting listener
     * @return ImportTask handle supporting cancellation and completion Future
     */
    public ImportTask scanAndImportAsync(Path rootPath, ImportProgressListener listener) {
        return scanAndImportAsync(rootPath, false, listener);
    }

    /**
     * Asynchronously scans the specified folder or file with optional forced metadata re-extraction.
     *
     * @param rootPath             directory or audio file path to import
     * @param forceMetadataRefresh true to re-extract metadata even if file modification time is unchanged
     * @param listener             progress reporting listener
     * @return ImportTask handle supporting cancellation and completion Future
     */
    public ImportTask scanAndImportAsync(Path rootPath, boolean forceMetadataRefresh, ImportProgressListener listener) {
        if (rootPath == null) {
            return scanAndImportAsync(java.util.Collections.emptyList(), forceMetadataRefresh, listener);
        }
        return scanAndImportAsync(List.of(rootPath), forceMetadataRefresh, listener);
    }

    /**
     * Rescans existing tracks and imports any new tracks in the specified folder or file,
     * unconditionally re-extracting audio metadata (BPM, bitrate, album artist, disc number, etc.).
     *
     * @param rootPath directory or audio file path to rescan
     * @param listener progress reporting listener
     * @return ImportTask handle supporting cancellation and completion Future
     */
    public ImportTask rescanAndImportAsync(Path rootPath, ImportProgressListener listener) {
        return scanAndImportAsync(rootPath, true, listener);
    }

    /**
     * Rescans existing tracks and imports any new tracks in multiple paths,
     * unconditionally re-extracting audio metadata (BPM, bitrate, album artist, disc number, etc.).
     *
     * @param paths    collection of directories or audio files to rescan
     * @param listener progress reporting listener
     * @return ImportTask handle supporting cancellation and completion Future
     */
    public ImportTask rescanAndImportAsync(java.util.Collection<Path> paths, ImportProgressListener listener) {
        return scanAndImportAsync(paths, true, listener);
    }

    /**
     * Asynchronously scans multiple files or directories and imports new/modified tracks into the library.
     *
     * @param paths    collection of directories or audio files
     * @param listener progress reporting listener
     * @return ImportTask handle supporting cancellation and completion Future
     */
    public ImportTask scanAndImportAsync(java.util.Collection<Path> paths, ImportProgressListener listener) {
        return scanAndImportAsync(paths, false, listener);
    }

    /**
     * Asynchronously scans multiple files or directories with optional forced metadata re-extraction.
     *
     * @param paths                collection of directories or audio files
     * @param forceMetadataRefresh true to re-extract metadata even if file modification time is unchanged
     * @param listener             progress reporting listener
     * @return ImportTask handle supporting cancellation and completion Future
     */
    public ImportTask scanAndImportAsync(java.util.Collection<Path> paths, boolean forceMetadataRefresh, ImportProgressListener listener) {
        ImportTask task = new ImportTask();

        executorService.submit(() -> {
            int addedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;
            List<Track> currentBatch = new ArrayList<>(batchSize);

            try {
                if (paths == null || paths.isEmpty()) {
                    dispatch(() -> {
                        if (listener != null) listener.onCompleted(ImportResult.EMPTY);
                        task.future.complete(0);
                        task.resultFuture.complete(ImportResult.EMPTY);
                    });
                    return;
                }

                // 1. Scan for audio files
                dispatch(() -> {
                    if (listener != null) listener.onProgress(0, 0, "");
                });

                List<Path> discoveredFiles = fileScanner.scanAll(paths, task.cancelFlag);
                int totalCount = discoveredFiles.size();

                if (task.isCancelled()) {
                    handleCancellation(listener, task, new ImportResult(addedCount, updatedCount, skippedCount));
                    return;
                }

                // 2. Build index of existing file paths for deduplication
                Map<String, Track> existingTracksByPath = new HashMap<>();
                for (Track t : library.getTracks()) {
                    if (t.getFilePath() != null && !t.getFilePath().isBlank()) {
                        existingTracksByPath.put(normalizePathKey(t.getFilePath()), t);
                    }
                }

                // 3. Process each discovered file with multi-core parallel extraction and pipeline window
                int availableCores = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 16));
                int parallelism = Math.max(1, Math.min(availableCores, batchSize * 2));
                ExecutorService extractionPool = Executors.newFixedThreadPool(parallelism, r -> {
                    Thread t = new Thread(r, "LibraryImporter-TagExtractor");
                    t.setDaemon(true);
                    return t;
                });

                try {
                    int windowCapacity = Math.max(parallelism, Math.min(batchSize * 2, parallelism * 4));
                    Queue<WorkItem> window = new ArrayDeque<>();
                    int producerIndex = 0;
                    int consumerIndex = 0;
                    long[] lastNotifyTimeRef = new long[]{0L};

                    while (consumerIndex < totalCount) {
                        if (task.isCancelled()) {
                            break;
                        }

                        // 1. Fill sliding window: pre-check existing and schedule extraction
                        while (producerIndex < totalCount && window.size() < windowCapacity && !task.isCancelled()) {
                            Path audioFile = discoveredFiles.get(producerIndex++);

                            String normalizedPath;
                            try {
                                normalizedPath = audioFile.toAbsolutePath().normalize().toString();
                            } catch (Exception e) {
                                normalizedPath = audioFile.toString();
                            }
                            String pathKey = normalizePathKey(normalizedPath);

                            Instant fileModified;
                            try {
                                fileModified = Files.getLastModifiedTime(audioFile).toInstant();
                            } catch (IOException e) {
                                fileModified = Instant.EPOCH;
                            }

                            Track existing = existingTracksByPath.get(pathKey);
                            if (existing == null) {
                                existing = library.findByFilePath(normalizedPath);
                            }

                            WorkItem item;
                            if (existing != null) {
                                Instant existingModified = existing.getLastModified();
                                if (!forceMetadataRefresh && existingModified != null && !fileModified.isAfter(existingModified)) {
                                    // File is unmodified -> skip immediately without metadata extraction
                                    item = new WorkItem(audioFile, pathKey, fileModified, WorkItemStatus.SKIP);
                                } else {
                                    // Existing file has been updated or forced refresh -> extract metadata
                                    item = new WorkItem(audioFile, pathKey, fileModified, WorkItemStatus.EXTRACT_FOR_UPDATE);
                                    item.extractionFuture = CompletableFuture.supplyAsync(() -> {
                                        if (task.isCancelled()) {
                                            return null;
                                        }
                                        try {
                                            return metadataExtractor.extract(audioFile);
                                        } catch (Exception e) {
                                            return null;
                                        }
                                    }, extractionPool);
                                }
                            } else {
                                // New file -> extract metadata
                                item = new WorkItem(audioFile, pathKey, fileModified, WorkItemStatus.EXTRACT_FOR_ADD);
                                item.extractionFuture = CompletableFuture.supplyAsync(() -> {
                                    if (task.isCancelled()) {
                                        return null;
                                    }
                                    try {
                                        return metadataExtractor.extract(audioFile);
                                    } catch (Exception e) {
                                        return null;
                                    }
                                }, extractionPool);
                            }

                            window.add(item);
                        }

                        // 2. Consume item from front of window
                        if (window.isEmpty()) {
                            break;
                        }

                        WorkItem item = window.poll();
                        consumerIndex++;
                        int processedIndex = consumerIndex;
                        String fileName = item.file.getFileName() != null ? item.file.getFileName().toString() : "";

                        // Throttled EDT progress dispatch
                        notifyProgressThrottled(listener, processedIndex, totalCount, fileName, lastNotifyTimeRef);

                        if (item.status == WorkItemStatus.SKIP) {
                            skippedCount++;
                        } else {
                            Track extracted = null;
                            try {
                                if (item.extractionFuture != null) {
                                    extracted = item.extractionFuture.join();
                                }
                            } catch (Exception e) {
                                extracted = null;
                            }

                            if (extracted == null) {
                                skippedCount++;
                            } else {
                                if (extracted.getLastModified() == null || extracted.getLastModified().equals(Instant.EPOCH)) {
                                    extracted.setLastModified(item.fileModified);
                                }

                                if (item.status == WorkItemStatus.EXTRACT_FOR_UPDATE) {
                                    Track upserted = library.upsertTrack(extracted);
                                    existingTracksByPath.put(item.pathKey, upserted);
                                    currentBatch.add(upserted);
                                    updatedCount++;
                                } else { // EXTRACT_FOR_ADD
                                    if (duplicatePolicy == DuplicatePolicy.SKIP_SAME_CONTENT && library.isContentDuplicate(extracted)) {
                                        skippedCount++;
                                    } else {
                                        library.addTrack(extracted);
                                        existingTracksByPath.put(item.pathKey, extracted);
                                        currentBatch.add(extracted);
                                        addedCount++;
                                    }
                                }
                            }
                        }

                        // Flush batch if size reached
                        if (currentBatch.size() >= batchSize) {
                            commitBatch(currentBatch, listener);
                            currentBatch.clear();
                        }
                    }
                } finally {
                    extractionPool.shutdownNow();
                }

                // Flush remaining tracks
                if (!currentBatch.isEmpty()) {
                    commitBatch(currentBatch, listener);
                    currentBatch.clear();
                }

                // 4. Save to persistence
                persistLibrary();

                // 5. Notify completion or cancellation
                ImportResult finalResult = new ImportResult(addedCount, updatedCount, skippedCount);
                if (task.isCancelled()) {
                    handleCancellation(listener, task, finalResult);
                } else {
                    dispatch(() -> {
                        if (listener != null) {
                            listener.onCompleted(finalResult);
                        }
                        task.future.complete(finalResult.getTotalImportedCount());
                        task.resultFuture.complete(finalResult);
                    });
                }

            } catch (Throwable t) {
                dispatch(() -> {
                    if (listener != null) {
                        listener.onError(t);
                    }
                    task.future.completeExceptionally(t);
                    task.resultFuture.completeExceptionally(t);
                });
            }
        });

        return task;
    }

    private void commitBatch(List<Track> batch, ImportProgressListener listener) {
        List<Track> copy = new ArrayList<>(batch);
        dispatch(() -> {
            if (listener != null) {
                listener.onBatchImported(copy);
            }
        });
    }

    private void handleCancellation(ImportProgressListener listener, ImportTask task, ImportResult result) {
        persistLibrary();
        dispatch(() -> {
            if (listener != null) {
                listener.onCancelled(result);
            }
            task.future.complete(result.getTotalImportedCount());
            task.resultFuture.complete(result);
        });
    }

    private void persistLibrary() {
        if (storage != null && storagePath != null) {
            try {
                storage.save(library, storagePath);
            } catch (IOException ignored) {
                // Ignore persistence failure during batch intermediate saves
            }
        }
    }

    private static String normalizePathKey(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return "";
        }
        try {
            return Paths.get(pathStr).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return pathStr.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static void dispatch(Runnable action) {
        if (SwingUtilities.isEventDispatchThread() || GraphicsEnvironment.isHeadless()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private static void notifyProgressThrottled(ImportProgressListener listener,
                                                int processedIndex,
                                                int totalCount,
                                                String fileName,
                                                long[] lastNotifyTimeRef) {
        if (listener == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean isFirst = (processedIndex == 1);
        boolean isLast = (processedIndex >= totalCount);
        boolean timeElapsed = (now - lastNotifyTimeRef[0] >= 100);
        boolean countInterval = (processedIndex % 50 == 0);

        if (isFirst || isLast || timeElapsed || countInterval) {
            lastNotifyTimeRef[0] = now;
            dispatch(() -> listener.onProgress(processedIndex, totalCount, fileName));
        }
    }

    private enum WorkItemStatus {
        SKIP,
        EXTRACT_FOR_UPDATE,
        EXTRACT_FOR_ADD
    }

    private static final class WorkItem {
        final Path file;
        final String pathKey;
        final Instant fileModified;
        final WorkItemStatus status;
        CompletableFuture<Track> extractionFuture;

        WorkItem(Path file, String pathKey, Instant fileModified, WorkItemStatus status) {
            this.file = file;
            this.pathKey = pathKey;
            this.fileModified = fileModified;
            this.status = status;
        }
    }

    /**
     * Handle representing an active import task.
     */
    public static class ImportTask {
        private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
        private final CompletableFuture<Integer> future = new CompletableFuture<>();
        private final CompletableFuture<ImportResult> resultFuture = new CompletableFuture<>();

        /**
         * Requests cancellation of this import operation.
         */
        public void cancel() {
            cancelFlag.set(true);
        }

        /**
         * Checks if cancellation has been requested.
         */
        public boolean isCancelled() {
            return cancelFlag.get();
        }

        /**
         * Returns the CompletableFuture resolving with the total imported track count (added + updated).
         */
        public CompletableFuture<Integer> getFuture() {
            return future;
        }

        /**
         * Returns the CompletableFuture resolving with detailed ImportResult statistics.
         */
        public CompletableFuture<ImportResult> getResultFuture() {
            return resultFuture;
        }
    }
}
