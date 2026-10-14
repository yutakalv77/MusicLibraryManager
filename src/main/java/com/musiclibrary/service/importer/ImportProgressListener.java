package com.musiclibrary.service.importer;

import com.musiclibrary.model.Track;

import java.util.List;

/**
 * Listener interface for monitoring asynchronous music library import operations.
 * Callbacks are dispatched safely on the Swing Event Dispatch Thread (EDT).
 */
public interface ImportProgressListener {

    /**
     * Called to report scanning and extraction progress.
     *
     * @param processedCount  number of files processed so far
     * @param totalCount      total number of audio files discovered (0 if still discovering)
     * @param currentFileName filename currently being parsed
     */
    void onProgress(int processedCount, int totalCount, String currentFileName);

    /**
     * Called when a batch of tracks has been parsed and added to the library.
     *
     * @param importedBatch list of extracted tracks in this batch
     */
    void onBatchImported(List<Track> importedBatch);

    /**
     * Called when the import operation completes successfully.
     *
     * @param totalImportedCount total number of tracks imported
     */
    void onCompleted(int totalImportedCount);

    /**
     * Called when the import operation completes successfully, providing detailed statistics.
     * Default implementation delegates to {@link #onCompleted(int)}.
     *
     * @param result detailed statistics of added, updated, and skipped tracks
     */
    default void onCompleted(ImportResult result) {
        onCompleted(result != null ? result.getTotalImportedCount() : 0);
    }

    /**
     * Called when the import operation is cancelled early by the user.
     *
     * @param partialImportedCount number of tracks imported before cancellation
     */
    void onCancelled(int partialImportedCount);

    /**
     * Called when the import operation is cancelled early by the user, providing detailed statistics.
     * Default implementation delegates to {@link #onCancelled(int)}.
     *
     * @param result detailed statistics before cancellation
     */
    default void onCancelled(ImportResult result) {
        onCancelled(result != null ? result.getTotalImportedCount() : 0);
    }

    /**
     * Called if an unrecoverable error occurs during the import process.
     *
     * @param error error details
     */
    void onError(Throwable error);
}
