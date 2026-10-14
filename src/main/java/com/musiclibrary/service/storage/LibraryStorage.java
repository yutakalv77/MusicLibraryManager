package com.musiclibrary.service.storage;

import com.musiclibrary.model.Library;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Storage service interface for saving and loading the music library.
 */
public interface LibraryStorage {

    /**
     * Saves the library contents to the specified file path.
     *
     * @param library    the library instance to save
     * @param targetPath the destination path
     * @throws IOException if an I/O error occurs during saving
     */
    void save(Library library, Path targetPath) throws IOException;

    /**
     * Loads the library contents from the specified file path.
     * If the target file does not exist, an empty Library should be returned.
     *
     * @param sourcePath the source file path
     * @return the loaded Library instance
     * @throws IOException if an I/O error occurs during loading
     */
    Library load(Path sourcePath) throws IOException;
}
