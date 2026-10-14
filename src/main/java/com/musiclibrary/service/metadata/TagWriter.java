package com.musiclibrary.service.metadata;

import com.musiclibrary.model.TrackMetadataUpdate;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Strategy interface for writing updated ID3/audio metadata back to audio files.
 */
public interface TagWriter {

    /**
     * Checks if this writer supports writing metadata for the given audio file format.
     *
     * @param filePath audio file path
     * @return true if format is supported, false otherwise
     */
    boolean supports(Path filePath);

    /**
     * Atomically writes updated metadata into the specified audio file.
     *
     * @param filePath audio file path to update
     * @param update   metadata changes to write
     * @throws IOException if an I/O error or tag encoding error occurs
     */
    void write(Path filePath, TrackMetadataUpdate update) throws IOException;
}
