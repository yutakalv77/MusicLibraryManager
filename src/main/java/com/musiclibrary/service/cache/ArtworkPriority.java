package com.musiclibrary.service.cache;

/**
 * Enumeration specifying the source priority when resolving album artwork images.
 */
public enum ArtworkPriority {

    /**
     * Prefer embedded audio file tags (ID3v2 APIC, etc.); fall back to folder images if absent.
     */
    EMBEDDED_FIRST,

    /**
     * Prefer external folder images (cover.jpg, folder.jpg, etc.); fall back to embedded tags if absent.
     */
    FOLDER_FIRST,

    /**
     * Only resolve artwork embedded within the audio file; ignore folder images.
     */
    EMBEDDED_ONLY,

    /**
     * Only resolve artwork from external folder images; ignore embedded audio tags.
     */
    FOLDER_ONLY
}
