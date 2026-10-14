package com.musiclibrary.service.importer;

/**
 * Policy governing deduplication behavior when importing audio files.
 */
public enum DuplicatePolicy {

    /**
     * Skips importing if a track with identical content (title, artist, duration, file size)
     * already exists in the library, even if located at a different file path.
     */
    SKIP_SAME_CONTENT,

    /**
     * Allows importing tracks located at different file paths, even if they share
     * identical content (title, artist, duration, file size).
     */
    ALLOW_SAME_CONTENT
}
