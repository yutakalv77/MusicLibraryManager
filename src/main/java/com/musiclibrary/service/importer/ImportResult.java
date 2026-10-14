package com.musiclibrary.service.importer;

/**
 * Encapsulates aggregate statistics for an audio import session.
 *
 * @param addedCount   number of newly added tracks
 * @param updatedCount number of existing tracks updated via Upsert
 * @param skippedCount number of duplicate or unmodified tracks skipped
 */
public record ImportResult(int addedCount, int updatedCount, int skippedCount) {

    /**
     * Creates an empty ImportResult with all counts set to zero.
     */
    public static final ImportResult EMPTY = new ImportResult(0, 0, 0);

    /**
     * Returns the total count of tracks that affected library content (added + updated).
     *
     * @return addedCount + updatedCount
     */
    public int getTotalImportedCount() {
        return addedCount + updatedCount;
    }

    /**
     * Returns the total number of audio files processed across all categories.
     *
     * @return addedCount + updatedCount + skippedCount
     */
    public int getTotalProcessedCount() {
        return addedCount + updatedCount + skippedCount;
    }
}
