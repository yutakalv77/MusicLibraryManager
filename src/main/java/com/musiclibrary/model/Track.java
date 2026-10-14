package com.musiclibrary.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing an audio track in the library.
 * Encapsulates track metadata, file information, and playback statistics.
 */
public class Track {

    private String id;
    private String filePath;
    private String title;
    private String artist;
    private String album;
    private String genre;
    private int year;
    private int trackNumber;
    private long durationSeconds;
    private long fileSizeBytes;
    private int rating;
    private int playCount;
    private Instant dateAdded;
    private Instant lastModified;
    private byte[] artworkData;
    private boolean hasArtwork;
    private transient boolean missing = false;

    /**
     * Default constructor initializing default values and a random UUID.
     */
    public Track() {
        this.id = UUID.randomUUID().toString();
        this.filePath = "";
        this.title = "";
        this.artist = "";
        this.album = "";
        this.genre = "";
        this.year = 0;
        this.trackNumber = 0;
        this.durationSeconds = 0;
        this.fileSizeBytes = 0;
        this.rating = 0;
        this.playCount = 0;
        this.dateAdded = Instant.now();
        this.lastModified = Instant.EPOCH;
        this.artworkData = null;
        this.hasArtwork = false;
    }

    private Track(Builder builder) {
        this.id = builder.id != null && !builder.id.isBlank() ? builder.id : UUID.randomUUID().toString();
        setFilePath(builder.filePath);
        this.title = builder.title != null ? builder.title : "";
        this.artist = builder.artist != null ? builder.artist : "";
        this.album = builder.album != null ? builder.album : "";
        this.genre = builder.genre != null ? builder.genre : "";
        setYear(builder.year);
        setTrackNumber(builder.trackNumber);
        setDurationSeconds(builder.durationSeconds);
        setFileSizeBytes(builder.fileSizeBytes);
        setRating(builder.rating);
        setPlayCount(builder.playCount);
        this.dateAdded = builder.dateAdded != null ? builder.dateAdded : Instant.now();
        this.lastModified = builder.lastModified != null ? builder.lastModified : Instant.EPOCH;
        if (builder.artworkData != null && builder.artworkData.length > 0) {
            setArtworkData(builder.artworkData);
        } else {
            this.artworkData = null;
            this.hasArtwork = builder.hasArtwork;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .filePath(this.filePath)
                .title(this.title)
                .artist(this.artist)
                .album(this.album)
                .genre(this.genre)
                .year(this.year)
                .trackNumber(this.trackNumber)
                .durationSeconds(this.durationSeconds)
                .fileSizeBytes(this.fileSizeBytes)
                .rating(this.rating)
                .playCount(this.playCount)
                .dateAdded(this.dateAdded)
                .lastModified(this.lastModified)
                .hasArtwork(this.hasArtwork())
                .artworkData(this.artworkData);
    }

    // --- Getters & Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Track id cannot be null or blank");
        }
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Track filePath cannot be null or blank");
        }
        this.filePath = filePath;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist != null ? artist : "";
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album != null ? album : "";
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre != null ? genre : "";
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        if (year < 0) {
            throw new IllegalArgumentException("Year cannot be negative: " + year);
        }
        this.year = year;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        if (trackNumber < 0) {
            throw new IllegalArgumentException("Track number cannot be negative: " + trackNumber);
        }
        this.trackNumber = trackNumber;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        if (durationSeconds < 0) {
            throw new IllegalArgumentException("Duration cannot be negative: " + durationSeconds);
        }
        this.durationSeconds = durationSeconds;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        if (fileSizeBytes < 0) {
            throw new IllegalArgumentException("File size cannot be negative: " + fileSizeBytes);
        }
        this.fileSizeBytes = fileSizeBytes;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 0 and 5: " + rating);
        }
        this.rating = rating;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        if (playCount < 0) {
            throw new IllegalArgumentException("Play count cannot be negative: " + playCount);
        }
        this.playCount = playCount;
    }

    /**
     * Increments the play count by 1.
     */
    public void incrementPlayCount() {
        this.playCount++;
    }

    public Instant getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(Instant dateAdded) {
        this.dateAdded = dateAdded != null ? dateAdded : Instant.now();
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified != null ? lastModified : Instant.EPOCH;
    }

    /**
     * Returns a defensive copy of the album artwork binary data, or null if none is present.
     * If not loaded in memory but marked with hasArtwork, loads lazily from the audio file on disk.
     */
    public byte[] getArtworkData() {
        if (artworkData != null) {
            return artworkData.clone();
        }
        if (hasArtwork && filePath != null && !filePath.isBlank()) {
            byte[] loaded = loadArtworkFromFile(filePath);
            if (loaded != null && loaded.length > 0) {
                return loaded;
            }
        }
        return null;
    }

    /**
     * Sets album artwork binary data using defensive copy and updates hasArtwork flag.
     */
    public void setArtworkData(byte[] artworkData) {
        this.artworkData = artworkData != null ? artworkData.clone() : null;
        this.hasArtwork = (this.artworkData != null && this.artworkData.length > 0);
    }

    /**
     * Checks if this track has artwork data embedded or available for lazy loading.
     */
    public boolean hasArtwork() {
        return hasArtwork || (artworkData != null && artworkData.length > 0);
    }

    /**
     * Updates the hasArtwork flag.
     */
    public void setHasArtwork(boolean hasArtwork) {
        this.hasArtwork = hasArtwork;
    }

    private static byte[] loadArtworkFromFile(String pathStr) {
        try {
            Path path = Path.of(pathStr);
            if (Files.exists(path)) {
                com.mpatric.mp3agic.Mp3File mp3File = new com.mpatric.mp3agic.Mp3File(path.toFile(), 65536, false);
                if (mp3File.hasId3v2Tag()) {
                    return mp3File.getId3v2Tag().getAlbumImage();
                }
            }
        } catch (Throwable ignored) {
            // Ignore I/O or tag reading failures gracefully
        }
        return null;
    }

    /**
     * Checks if the backing file for this track is missing on disk.
     */
    public boolean isMissing() {
        return missing;
    }

    /**
     * Updates the missing status of this track.
     */
    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    // --- Standard Methods ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Track track = (Track) o;
        return Objects.equals(id, track.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Track{" +
                "id='" + id + '\'' +
                ", filePath='" + filePath + '\'' +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", album='" + album + '\'' +
                ", genre='" + genre + '\'' +
                ", year=" + year +
                ", trackNumber=" + trackNumber +
                ", durationSeconds=" + durationSeconds +
                ", fileSizeBytes=" + fileSizeBytes +
                ", rating=" + rating +
                ", playCount=" + playCount +
                ", dateAdded=" + dateAdded +
                ", lastModified=" + lastModified +
                ", hasArtwork=" + hasArtwork() +
                ", artworkData=" + (artworkData != null ? "[" + artworkData.length + " bytes]" : "null") +
                '}';
    }

    // --- Builder Pattern ---

    public static class Builder {
        private String id;
        private String filePath;
        private String title = "";
        private String artist = "";
        private String album = "";
        private String genre = "";
        private int year = 0;
        private int trackNumber = 0;
        private long durationSeconds = 0;
        private long fileSizeBytes = 0;
        private int rating = 0;
        private int playCount = 0;
        private Instant dateAdded = Instant.now();
        private Instant lastModified = Instant.EPOCH;
        private byte[] artworkData;
        private boolean hasArtwork = false;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder artist(String artist) {
            this.artist = artist;
            return this;
        }

        public Builder album(String album) {
            this.album = album;
            return this;
        }

        public Builder genre(String genre) {
            this.genre = genre;
            return this;
        }

        public Builder year(int year) {
            this.year = year;
            return this;
        }

        public Builder trackNumber(int trackNumber) {
            this.trackNumber = trackNumber;
            return this;
        }

        public Builder durationSeconds(long durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }

        public Builder fileSizeBytes(long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
            return this;
        }

        public Builder rating(int rating) {
            this.rating = rating;
            return this;
        }

        public Builder playCount(int playCount) {
            this.playCount = playCount;
            return this;
        }

        public Builder dateAdded(Instant dateAdded) {
            this.dateAdded = dateAdded;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder artworkData(byte[] artworkData) {
            this.artworkData = artworkData != null ? artworkData.clone() : null;
            if (this.artworkData != null && this.artworkData.length > 0) {
                this.hasArtwork = true;
            }
            return this;
        }

        public Builder hasArtwork(boolean hasArtwork) {
            this.hasArtwork = hasArtwork;
            return this;
        }

        public Track build() {
            return new Track(this);
        }
    }
}
