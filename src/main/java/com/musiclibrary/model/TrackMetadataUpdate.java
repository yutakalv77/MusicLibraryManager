package com.musiclibrary.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Data transfer object encapsulating partial or full updates to a track's ID3 metadata.
 * Tracks change-flags for each field to support both single-track and batch multi-track editing.
 */
public class TrackMetadataUpdate {

    private final String title;
    private final boolean updateTitle;

    private final String artist;
    private final boolean updateArtist;

    private final String album;
    private final boolean updateAlbum;

    private final String genre;
    private final boolean updateGenre;

    private final int year;
    private final boolean updateYear;

    private final int trackNumber;
    private final boolean updateTrackNumber;

    private final byte[] artworkData;
    private final String artworkMimeType;
    private final boolean clearArtwork;
    private final boolean updateArtwork;

    private TrackMetadataUpdate(Builder builder) {
        this.title = builder.title;
        this.updateTitle = builder.updateTitle;

        this.artist = builder.artist;
        this.updateArtist = builder.updateArtist;

        this.album = builder.album;
        this.updateAlbum = builder.updateAlbum;

        this.genre = builder.genre;
        this.updateGenre = builder.updateGenre;

        this.year = builder.year;
        this.updateYear = builder.updateYear;

        this.trackNumber = builder.trackNumber;
        this.updateTrackNumber = builder.updateTrackNumber;

        this.artworkData = builder.artworkData != null ? builder.artworkData.clone() : null;
        this.artworkMimeType = builder.artworkMimeType;
        this.clearArtwork = builder.clearArtwork;
        this.updateArtwork = builder.updateArtwork;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Applies this update's enabled fields onto the specified Track entity.
     *
     * @param track target track entity
     */
    public void applyTo(Track track) {
        if (track == null) {
            return;
        }
        if (updateTitle && title != null) {
            track.setTitle(title.trim());
        }
        if (updateArtist && artist != null) {
            track.setArtist(artist.trim());
        }
        if (updateAlbum && album != null) {
            track.setAlbum(album.trim());
        }
        if (updateGenre && genre != null) {
            track.setGenre(genre.trim());
        }
        if (updateYear) {
            track.setYear(Math.max(0, year));
        }
        if (updateTrackNumber) {
            track.setTrackNumber(Math.max(0, trackNumber));
        }
        if (updateArtwork) {
            if (clearArtwork) {
                track.setArtworkData(null);
            } else if (artworkData != null) {
                track.setArtworkData(artworkData.clone());
            }
        }
    }

    // --- Getters ---

    public String getTitle() {
        return title;
    }

    public boolean isUpdateTitle() {
        return updateTitle;
    }

    public String getArtist() {
        return artist;
    }

    public boolean isUpdateArtist() {
        return updateArtist;
    }

    public String getAlbum() {
        return album;
    }

    public boolean isUpdateAlbum() {
        return updateAlbum;
    }

    public String getGenre() {
        return genre;
    }

    public boolean isUpdateGenre() {
        return updateGenre;
    }

    public int getYear() {
        return year;
    }

    public boolean isUpdateYear() {
        return updateYear;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public boolean isUpdateTrackNumber() {
        return updateTrackNumber;
    }

    public byte[] getArtworkData() {
        return artworkData != null ? artworkData.clone() : null;
    }

    public String getArtworkMimeType() {
        return artworkMimeType;
    }

    public boolean isClearArtwork() {
        return clearArtwork;
    }

    public boolean isUpdateArtwork() {
        return updateArtwork;
    }

    // --- Builder ---

    public static class Builder {
        private String title;
        private boolean updateTitle = false;

        private String artist;
        private boolean updateArtist = false;

        private String album;
        private boolean updateAlbum = false;

        private String genre;
        private boolean updateGenre = false;

        private int year = 0;
        private boolean updateYear = false;

        private int trackNumber = 0;
        private boolean updateTrackNumber = false;

        private byte[] artworkData;
        private String artworkMimeType = "image/jpeg";
        private boolean clearArtwork = false;
        private boolean updateArtwork = false;

        public Builder title(String title) {
            this.title = title;
            this.updateTitle = true;
            return this;
        }

        public Builder artist(String artist) {
            this.artist = artist;
            this.updateArtist = true;
            return this;
        }

        public Builder album(String album) {
            this.album = album;
            this.updateAlbum = true;
            return this;
        }

        public Builder genre(String genre) {
            this.genre = genre;
            this.updateGenre = true;
            return this;
        }

        public Builder year(int year) {
            this.year = Math.max(0, year);
            this.updateYear = true;
            return this;
        }

        public Builder trackNumber(int trackNumber) {
            this.trackNumber = Math.max(0, trackNumber);
            this.updateTrackNumber = true;
            return this;
        }

        public Builder artwork(byte[] data, String mimeType) {
            this.artworkData = data != null ? data.clone() : null;
            this.artworkMimeType = (mimeType != null && !mimeType.isBlank()) ? mimeType : "image/jpeg";
            this.clearArtwork = false;
            this.updateArtwork = true;
            return this;
        }

        public Builder clearArtwork() {
            this.artworkData = null;
            this.clearArtwork = true;
            this.updateArtwork = true;
            return this;
        }

        public TrackMetadataUpdate build() {
            return new TrackMetadataUpdate(this);
        }
    }
}
