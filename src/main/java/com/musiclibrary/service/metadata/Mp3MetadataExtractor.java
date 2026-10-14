package com.musiclibrary.service.metadata;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v2Frame;
import com.mpatric.mp3agic.ID3v2FrameSet;
import com.mpatric.mp3agic.ID3v2TextFrameData;
import com.mpatric.mp3agic.Mp3File;
import com.musiclibrary.model.Track;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata extractor for MP3 audio files using the mp3agic library.
 * Reads ID3v2 and ID3v1 tags, extracts embedded artwork, and applies smart fallback
 * completion for missing tags (title/track number from filename, artist/album from directory tree).
 */
public class Mp3MetadataExtractor implements MetadataExtractor {

    private static final Pattern LEADING_TRACK_NUMBER_PATTERN = Pattern.compile("^(\\d+)[\\s._-]+(.*)$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");
    private static final Pattern TLEN_NUMERIC_PATTERN = Pattern.compile("^(\\d+)");
    private static final int DEFAULT_BUFFER_LENGTH = 65536;

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
    public Track extract(Path filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath cannot be null");
        }
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }

        String absolutePath = filePath.toAbsolutePath().normalize().toString();
        long fileSize = Files.size(filePath);
        Instant lastModified = Files.getLastModifiedTime(filePath).toInstant();

        String title = "";
        String artist = "";
        String album = "";
        String genre = "";
        int year = 0;
        int trackNumber = 0;
        long durationSeconds = 0;
        boolean hasArtwork = false;

        // Try reading with mp3agic using fast metadata scan (scanFile=false, bufferLength=65536)
        // to avoid scanning through all audio frames (saves huge disk I/O on large libraries).
        try {
            Mp3File mp3File = new Mp3File(filePath.toFile(), DEFAULT_BUFFER_LENGTH, false);
            durationSeconds = Math.max(0, mp3File.getLengthInSeconds());

            // 1. Check ID3v2 tag first (higher fidelity)
            if (mp3File.hasId3v2Tag()) {
                ID3v2 id3v2 = mp3File.getId3v2Tag();
                title = cleanString(id3v2.getTitle());
                artist = cleanString(id3v2.getArtist());
                album = cleanString(id3v2.getAlbum());
                genre = cleanString(id3v2.getGenreDescription());
                if (genre.isEmpty() && id3v2.getGenre() >= 0) {
                    genre = String.valueOf(id3v2.getGenre());
                }
                year = parseYear(id3v2.getYear());
                trackNumber = parseTrackNumber(id3v2.getTrack());
                
                // Check if artwork frame is present without loading/holding large binary in memory
                if (id3v2.getFrameSets() != null &&
                        (id3v2.getFrameSets().containsKey("APIC") || id3v2.getFrameSets().containsKey("PIC"))) {
                    hasArtwork = true;
                } else {
                    hasArtwork = (id3v2.getAlbumImage() != null);
                }

                // If duration is 0, attempt to retrieve duration from TLEN/TLE frame in ID3v2
                if (durationSeconds <= 0) {
                    durationSeconds = extractDurationFromTlen(id3v2);
                }
            }

            // 2. Check ID3v1 tag as fallback
            if (mp3File.hasId3v1Tag()) {
                ID3v1 id3v1 = mp3File.getId3v1Tag();
                if (title.isEmpty()) title = cleanString(id3v1.getTitle());
                if (artist.isEmpty()) artist = cleanString(id3v1.getArtist());
                if (album.isEmpty()) album = cleanString(id3v1.getAlbum());
                if (genre.isEmpty()) genre = cleanString(id3v1.getGenreDescription());
                if (year <= 0) year = parseYear(id3v1.getYear());
                if (trackNumber <= 0) trackNumber = parseTrackNumber(id3v1.getTrack());
            }

            // 3. Fallback: Fast estimation from audio data size and bitrate if duration is still 0
            if (durationSeconds <= 0) {
                durationSeconds = estimateDurationFromBitrate(mp3File, fileSize);
            }
        } catch (Exception e) {
            // Safe fallback: file is corrupt, 0-byte, or has invalid tags.
            // Continue with default values and smart filename completion.
        }

        // 3. Smart completion for missing metadata
        String rawFileName = filePath.getFileName() != null ? filePath.getFileName().toString() : "";
        String baseName = stripExtension(rawFileName);

        // Smart Title & Track Number from filename
        Matcher matcher = LEADING_TRACK_NUMBER_PATTERN.matcher(baseName);
        if (matcher.matches()) {
            if (trackNumber <= 0) {
                try {
                    trackNumber = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
            if (title.isEmpty()) {
                String potentialTitle = matcher.group(2).trim();
                title = !potentialTitle.isEmpty() ? potentialTitle : baseName;
            }
        } else {
            if (title.isEmpty()) {
                title = baseName.trim();
            }
        }

        // If title is still empty (e.g. file was just ".mp3"), fallback to base filename
        if (title.isEmpty()) {
            title = baseName.isEmpty() ? "Unknown Track" : baseName;
        }

        // Smart Album from parent directory
        if (album.isEmpty()) {
            Path parentDir = filePath.getParent();
            if (parentDir != null && parentDir.getFileName() != null) {
                album = parentDir.getFileName().toString().trim();
            }
        }

        // Smart Artist from parent of parent directory
        if (artist.isEmpty()) {
            Path parentDir = filePath.getParent();
            if (parentDir != null) {
                Path grandParentDir = parentDir.getParent();
                if (grandParentDir != null && grandParentDir.getFileName() != null) {
                    artist = grandParentDir.getFileName().toString().trim();
                }
            }
        }

        return Track.builder()
                .filePath(absolutePath)
                .title(title)
                .artist(artist)
                .album(album)
                .genre(genre)
                .year(year)
                .trackNumber(trackNumber)
                .durationSeconds(durationSeconds)
                .fileSizeBytes(fileSize)
                .hasArtwork(hasArtwork)
                .dateAdded(Instant.now())
                .lastModified(lastModified)
                .build();
    }

    private static String cleanString(String input) {
        if (input == null) {
            return "";
        }
        return input.trim();
    }

    private static String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private static int parseTrackNumber(String track) {
        if (track == null || track.isBlank()) {
            return 0;
        }
        String clean = track.trim();
        int slashIndex = clean.indexOf('/');
        if (slashIndex > 0) {
            clean = clean.substring(0, slashIndex).trim();
        }
        try {
            int num = Integer.parseInt(clean);
            return Math.max(0, num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseYear(String yearStr) {
        if (yearStr == null || yearStr.isBlank()) {
            return 0;
        }
        Matcher matcher = YEAR_PATTERN.matcher(yearStr);
        if (matcher.find()) {
            try {
                int y = Integer.parseInt(matcher.group(1));
                return Math.max(0, y);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    /* package-private for testing */
    static long extractDurationFromTlen(ID3v2 id3v2) {
        if (id3v2 == null || id3v2.getFrameSets() == null) {
            return 0;
        }
        // Check "TLEN" (ID3v2.3, ID3v2.4) or "TLE" (ID3v2.2)
        ID3v2FrameSet frameSet = id3v2.getFrameSets().get("TLEN");
        if (frameSet == null || frameSet.getFrames().isEmpty()) {
            frameSet = id3v2.getFrameSets().get("TLE");
        }
        if (frameSet != null && !frameSet.getFrames().isEmpty()) {
            for (ID3v2Frame frame : frameSet.getFrames()) {
                byte[] data = frame.getData();
                if (data != null && data.length > 0) {
                    try {
                        ID3v2TextFrameData textFrameData = new ID3v2TextFrameData(id3v2.hasUnsynchronisation(), data);
                        if (textFrameData.getText() != null) {
                            long ms = parseTlenString(textFrameData.getText().toString());
                            if (ms > 0) {
                                return Math.round((double) ms / 1000.0);
                            }
                        }
                    } catch (Exception ignored) {
                        // Fallback to raw string parsing if ID3v2TextFrameData fails
                        String raw = extractTextFromBytes(data);
                        long ms = parseTlenString(raw);
                        if (ms > 0) {
                            return Math.round((double) ms / 1000.0);
                        }
                    }
                }
            }
        }
        return 0;
    }

    /* package-private for testing */
    static long parseTlenString(String str) {
        if (str == null || str.isBlank()) {
            return 0;
        }
        String clean = str.replace("\0", "").trim();
        Matcher matcher = TLEN_NUMERIC_PATTERN.matcher(clean);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String extractTextFromBytes(byte[] data) {
        if (data == null || data.length <= 1) {
            return "";
        }
        // ID3v2 text frame body: first byte is encoding, subsequent bytes are text
        return new String(data, 1, data.length - 1, StandardCharsets.ISO_8859_1);
    }

    /* package-private for testing */
    static long estimateDurationFromBitrate(Mp3File mp3File, long fileSize) {
        if (fileSize <= 0) {
            return 0;
        }
        long startOffset = mp3File.getStartOffset() >= 0 ? mp3File.getStartOffset() : 0;
        long endOffset = fileSize - (mp3File.hasId3v1Tag() ? 128 : 0);
        long audioDataBytes = endOffset - startOffset;
        int bitrate = mp3File.getBitrate();
        return calculateDurationFromAudioData(audioDataBytes, bitrate);
    }

    /* package-private for testing */
    static long calculateDurationFromAudioData(long audioDataBytes, int bitrate) {
        if (audioDataBytes <= 0) {
            return 0;
        }
        if (bitrate <= 0) {
            bitrate = 128; // Default standard MP3 bitrate (128 kbps)
        }
        long duration = Math.round((double) (audioDataBytes * 8L) / (bitrate * 1000.0));
        return Math.max(0, duration);
    }
}
