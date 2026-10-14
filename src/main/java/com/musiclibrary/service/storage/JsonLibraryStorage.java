package com.musiclibrary.service.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-based implementation of LibraryStorage using Google Gson.
 * Features pretty printing, ISO-8601 timestamp handling, and atomic write operations
 * to prevent library corruption on crash or abrupt shutdown.
 */
public class JsonLibraryStorage implements LibraryStorage {

    private final Gson gson;

    /**
     * Constructs a JsonLibraryStorage instance with custom Gson configuration.
     */
    public JsonLibraryStorage() {
        this.gson = createGson();
    }

    /**
     * Constructs a JsonLibraryStorage instance with a custom Gson instance.
     *
     * @param gson customized Gson instance
     */
    public JsonLibraryStorage(Gson gson) {
        this.gson = gson != null ? gson : createGson();
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .registerTypeAdapter(byte[].class, new ByteArrayTypeAdapter())
                .create();
    }

    public static final String BACKUP_EXTENSION = ".bak";
    public static final String TEMP_EXTENSION = ".tmp";
    public static final long EMPTY_JSON_THRESHOLD_BYTES = 100;

    @Override
    public void save(Library library, Path targetPath) throws IOException {
        if (library == null) {
            throw new IllegalArgumentException("Library cannot be null");
        }
        if (targetPath == null) {
            throw new IllegalArgumentException("Target path cannot be null");
        }

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String fileName = targetPath.getFileName().toString();
        Path backupPath = targetPath.resolveSibling(fileName + BACKUP_EXTENSION);
        Path tempPath = targetPath.resolveSibling(fileName + TEMP_EXTENSION);

        // 1. Guard against accidental overwrites and create backup
        if (Files.exists(targetPath)) {
            long existingSize = Files.size(targetPath);
            int trackCount = library.getTrackCount();

            if (trackCount == 0 && existingSize > EMPTY_JSON_THRESHOLD_BYTES) {
                // Safeguard: Do not overwrite an existing populated backup with empty state!
                if (!Files.exists(backupPath)) {
                    Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }
                System.err.println("WARNING: Overwriting populated library (" + existingSize
                        + " bytes) with 0 tracks. Existing backup preserved: " + backupPath);
            } else if (existingSize > 0) {
                // Normal backup rotation
                try {
                    Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Notice: Failed to create library backup: " + e.getMessage());
                }
            }
        }

        // 2. Prepare DTO
        LibraryDto dto = new LibraryDto();
        dto.tracks = new HashMap<>(library.getTrackMap());
        dto.playlists = new ArrayList<>(library.getPlaylists());

        // 3. Write to temporary file in the same directory first
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                gson.toJson(dto, writer);
            }

            // 4. Atomic move to destination
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback for file systems not supporting atomic move
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public Library load(Path sourcePath) throws IOException {
        if (sourcePath == null) {
            throw new IllegalArgumentException("Source path cannot be null");
        }

        String fileName = sourcePath.getFileName().toString();
        Path backupPath = sourcePath.resolveSibling(fileName + BACKUP_EXTENSION);

        boolean primaryExists = Files.exists(sourcePath);
        long primarySize = primaryExists ? Files.size(sourcePath) : 0;

        // 1. Case: primary file is missing or 0 bytes -> attempt restore from backup
        if (!primaryExists || primarySize == 0) {
            if (Files.exists(backupPath) && Files.size(backupPath) > 0) {
                System.out.println("Notice: Primary library file missing or empty. Restoring from backup: " + backupPath);
                try {
                    Files.copy(backupPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
                    return parseLibraryFile(sourcePath);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to restore library from backup: " + e.getMessage());
                }
            }
            return new Library();
        }

        // 2. Case: primary file exists, try parsing
        try {
            Library loaded = parseLibraryFile(sourcePath);
            // If primary file has 0 tracks but backup has valid content, attempt recovery
            if (loaded.getTrackCount() == 0 && primarySize <= EMPTY_JSON_THRESHOLD_BYTES) {
                if (Files.exists(backupPath) && Files.size(backupPath) > EMPTY_JSON_THRESHOLD_BYTES) {
                    try {
                        Library backupLib = parseLibraryFile(backupPath);
                        if (backupLib.getTrackCount() > 0) {
                            System.out.println("Notice: Primary library is empty but backup contains "
                                    + backupLib.getTrackCount() + " tracks. Auto-recovering from backup.");
                            Files.copy(backupPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
                            return backupLib;
                        }
                    } catch (Exception e) {
                        System.err.println("Warning: Backup recovery failed: " + e.getMessage());
                    }
                }
            }
            return loaded;
        } catch (Exception parseException) {
            // 3. Primary file corrupted: attempt fallback to backup
            if (Files.exists(backupPath) && Files.size(backupPath) > 0) {
                System.err.println("Warning: Primary library corrupted. Falling back to backup: " + backupPath);
                try {
                    Library backupLib = parseLibraryFile(backupPath);
                    Files.copy(backupPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
                    return backupLib;
                } catch (Exception backupException) {
                    System.err.println("Error: Both primary and backup library files failed to load: " + backupException.getMessage());
                }
            }
            throw parseException;
        }
    }

    private Library parseLibraryFile(Path path) throws IOException {
        LibraryDto dto;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            dto = gson.fromJson(reader, LibraryDto.class);
        }

        Library library = new Library();
        if (dto != null) {
            if (dto.tracks != null) {
                library.addTracks(dto.tracks.values());
            }
            if (dto.playlists != null) {
                for (Playlist playlist : dto.playlists) {
                    if (playlist != null) {
                        library.addPlaylist(playlist);
                    }
                }
            }
        }

        return library;
    }

    /**
     * DTO for representing the library JSON structure cleanly.
     */
    static class LibraryDto {
        Map<String, Track> tracks;
        List<Playlist> playlists;
    }

    /**
     * TypeAdapter for serializing and deserializing java.time.Instant as ISO-8601 strings.
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String text = in.nextString();
            return (text != null && !text.isBlank()) ? Instant.parse(text) : null;
        }
    }

    /**
     * TypeAdapter for serializing byte[] as Base64 string for compact representation,
     * while supporting both Base64 string and numerical JSON array during deserialization
     * for full backwards compatibility.
     */
    private static class ByteArrayTypeAdapter extends TypeAdapter<byte[]> {
        @Override
        public void write(JsonWriter out, byte[] value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(Base64.getEncoder().encodeToString(value));
            }
        }

        @Override
        public byte[] read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            if (in.peek() == JsonToken.STRING) {
                String str = in.nextString();
                if (str.isEmpty()) {
                    return new byte[0];
                }
                return Base64.getDecoder().decode(str);
            }
            if (in.peek() == JsonToken.BEGIN_ARRAY) {
                in.beginArray();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (in.hasNext()) {
                    baos.write((byte) in.nextInt());
                }
                in.endArray();
                return baos.toByteArray();
            }
            in.skipValue();
            return null;
        }
    }
}
