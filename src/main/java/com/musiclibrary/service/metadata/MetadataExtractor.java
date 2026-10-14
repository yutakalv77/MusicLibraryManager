package com.musiclibrary.service.metadata;

import com.musiclibrary.model.Track;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Service interface for extracting track metadata from audio files.
 */
public interface MetadataExtractor {

    /**
     * Extracts metadata from the audio file and constructs a Track entity.
     *
     * @param filePath path to audio file
     * @return Track populated with metadata
     * @throws IOException if the file cannot be accessed
     */
    Track extract(Path filePath) throws IOException;

    /**
     * Checks if this extractor supports processing the given file path.
     *
     * @param filePath path to check
     * @return true if supported, false otherwise
     */
    boolean supports(Path filePath);
}
