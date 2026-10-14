package com.musiclibrary.service.metadata;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.Mp3File;
import com.musiclibrary.model.TrackMetadataUpdate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * High-reliability ID3 tag writer for MP3 audio files using the mp3agic library.
 * Writes ID3v2.4 and syncs ID3v1 tags atomically via temporary file substitution
 * to guarantee non-destructive file operations.
 */
public class Mp3TagWriter implements TagWriter {

    @Override
    public boolean supports(Path filePath) {
        if (filePath == null) {
            return false;
        }
        Path fileName = filePath.getFileName();
        if (fileName == null) {
            return false;
        }
        return fileName.toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    @Override
    public void write(Path filePath, TrackMetadataUpdate update) throws IOException {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        Objects.requireNonNull(update, "update cannot be null");

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("Target audio file does not exist: " + filePath);
        }

        Mp3File mp3file;
        try {
            mp3file = new Mp3File(filePath.toFile());
        } catch (Exception e) {
            throw new IOException("Failed to parse MP3 file for tag writing: " + filePath + " (" + e.getMessage() + ")", e);
        }

        // 1. Obtain or create ID3v2 tag
        ID3v2 id3v2;
        if (mp3file.hasId3v2Tag()) {
            id3v2 = mp3file.getId3v2Tag();
        } else {
            id3v2 = new ID3v24Tag();
            mp3file.setId3v2Tag(id3v2);
        }

        // 2. Apply ID3v2 updates
        if (update.isUpdateTitle() && update.getTitle() != null) {
            id3v2.setTitle(update.getTitle());
        }
        if (update.isUpdateArtist() && update.getArtist() != null) {
            id3v2.setArtist(update.getArtist());
        }
        if (update.isUpdateAlbum() && update.getAlbum() != null) {
            id3v2.setAlbum(update.getAlbum());
        }
        if (update.isUpdateGenre() && update.getGenre() != null) {
            id3v2.setGenreDescription(update.getGenre());
        }
        if (update.isUpdateYear()) {
            id3v2.setYear(update.getYear() > 0 ? String.valueOf(update.getYear()) : "");
        }
        if (update.isUpdateTrackNumber()) {
            id3v2.setTrack(update.getTrackNumber() > 0 ? String.valueOf(update.getTrackNumber()) : "");
        }
        if (update.isUpdateArtwork()) {
            if (update.isClearArtwork()) {
                id3v2.clearAlbumImage();
            } else if (update.getArtworkData() != null) {
                String mime = (update.getArtworkMimeType() != null && !update.getArtworkMimeType().isBlank())
                        ? update.getArtworkMimeType()
                        : "image/jpeg";
                id3v2.setAlbumImage(update.getArtworkData(), mime);
            }
        }

        // 3. Sync ID3v1 tag if present
        if (mp3file.hasId3v1Tag()) {
            ID3v1 id3v1 = mp3file.getId3v1Tag();
            if (update.isUpdateTitle() && update.getTitle() != null) {
                id3v1.setTitle(trimToLength(update.getTitle(), 30));
            }
            if (update.isUpdateArtist() && update.getArtist() != null) {
                id3v1.setArtist(trimToLength(update.getArtist(), 30));
            }
            if (update.isUpdateAlbum() && update.getAlbum() != null) {
                id3v1.setAlbum(trimToLength(update.getAlbum(), 30));
            }
            if (update.isUpdateYear()) {
                id3v1.setYear(update.getYear() > 0 ? String.valueOf(update.getYear()) : "");
            }
            if (update.isUpdateTrackNumber()) {
                id3v1.setTrack(update.getTrackNumber() > 0 ? String.valueOf(update.getTrackNumber()) : "");
            }
        }

        // 4. Atomic file save and replacement
        Path parentDir = filePath.getParent();
        String tempFileName = ".tmp_tag_" + UUID.randomUUID().toString().replace("-", "") + ".mp3";
        Path tempFile = (parentDir != null) ? parentDir.resolve(tempFileName) : Path.of(tempFileName);

        try {
            mp3file.save(tempFile.toString());

            // Move temp file over destination atomically
            try {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IOException("Failed to save updated ID3 tags to " + filePath + ": " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }

    private static String trimToLength(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() <= maxLen ? str : str.substring(0, maxLen);
    }
}
