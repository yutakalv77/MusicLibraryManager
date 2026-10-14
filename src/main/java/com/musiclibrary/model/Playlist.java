package com.musiclibrary.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a user-defined playlist.
 * Stores references to tracks via track ID list to prevent data desynchronization.
 */
public class Playlist {

    private String id;
    private String name;
    private final List<String> trackIds;

    /**
     * Default constructor for serialization and blank initialization.
     */
    public Playlist() {
        this(UUID.randomUUID().toString(), "Untitled Playlist", Collections.emptyList());
    }

    /**
     * Creates a new playlist with the specified name and an auto-generated UUID.
     *
     * @param name playlist name
     */
    public Playlist(String name) {
        this(UUID.randomUUID().toString(), name, Collections.emptyList());
    }

    /**
     * Creates a playlist with a specific ID and name.
     *
     * @param id   unique identifier
     * @param name playlist name
     */
    public Playlist(String id, String name) {
        this(id, name, Collections.emptyList());
    }

    /**
     * Creates a playlist with a specific ID, name, and initial track IDs.
     *
     * @param id       unique identifier
     * @param name     playlist name
     * @param trackIds list of track IDs
     */
    public Playlist(String id, String name, List<String> trackIds) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        setName(name);
        this.trackIds = new ArrayList<>();
        if (trackIds != null) {
            for (String trackId : trackIds) {
                if (trackId != null && !trackId.isBlank()) {
                    this.trackIds.add(trackId);
                }
            }
        }
    }

    // --- Getters & Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Playlist id cannot be null or blank");
        }
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name != null && !name.isBlank()) ? name.trim() : "Untitled Playlist";
    }

    /**
     * Returns an unmodifiable view of track IDs in this playlist.
     *
     * @return unmodifiable list of track IDs
     */
    public List<String> getTrackIds() {
        return Collections.unmodifiableList(trackIds);
    }

    /**
     * Replaces track IDs in the playlist with a defensive copy.
     *
     * @param newTrackIds list of new track IDs
     */
    public void setTrackIds(List<String> newTrackIds) {
        this.trackIds.clear();
        if (newTrackIds != null) {
            for (String trackId : newTrackIds) {
                if (trackId != null && !trackId.isBlank()) {
                    this.trackIds.add(trackId);
                }
            }
        }
    }

    // --- Track Management ---

    /**
     * Appends a track ID to the end of the playlist.
     *
     * @param trackId ID of track to add
     * @throws IllegalArgumentException if trackId is null or blank
     */
    public void addTrack(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            throw new IllegalArgumentException("trackId cannot be null or blank");
        }
        this.trackIds.add(trackId);
    }

    /**
     * Inserts a track ID at the specified index.
     *
     * @param index   position to insert
     * @param trackId ID of track to add
     * @throws IndexOutOfBoundsException if index is out of bounds
     * @throws IllegalArgumentException  if trackId is null or blank
     */
    public void addTrack(int index, String trackId) {
        if (trackId == null || trackId.isBlank()) {
            throw new IllegalArgumentException("trackId cannot be null or blank");
        }
        if (index < 0 || index > this.trackIds.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + this.trackIds.size());
        }
        this.trackIds.add(index, trackId);
    }

    /**
     * Removes the first occurrence of the specified track ID from the playlist.
     *
     * @param trackId ID of track to remove
     * @return true if the track was present and removed, false otherwise
     */
    public boolean removeTrack(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return false;
        }
        return this.trackIds.remove(trackId);
    }

    /**
     * Removes the track at the specified index.
     *
     * @param index index of track to remove
     * @return removed track ID
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public String removeTrackAt(int index) {
        if (index < 0 || index >= this.trackIds.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + this.trackIds.size());
        }
        return this.trackIds.remove(index);
    }

    /**
     * Reorders a track from the source index to the target index.
     *
     * @param fromIndex source position
     * @param toIndex   target position
     * @throws IndexOutOfBoundsException if either index is out of bounds
     */
    public void reorder(int fromIndex, int toIndex) {
        int size = this.trackIds.size();
        if (fromIndex < 0 || fromIndex >= size) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + ", Size: " + size);
        }
        if (toIndex < 0 || toIndex >= size) {
            throw new IndexOutOfBoundsException("toIndex: " + toIndex + ", Size: " + size);
        }
        if (fromIndex == toIndex) {
            return;
        }

        String item = this.trackIds.remove(fromIndex);
        this.trackIds.add(toIndex, item);
    }

    /**
     * Returns the number of tracks in this playlist.
     *
     * @return track count
     */
    public int getTrackCount() {
        return trackIds.size();
    }

    /**
     * Checks if this playlist contains the given track ID.
     *
     * @param trackId track ID to check
     * @return true if contained, false otherwise
     */
    public boolean containsTrack(String trackId) {
        return trackIds.contains(trackId);
    }

    /**
     * Removes all tracks from this playlist.
     */
    public void clear() {
        this.trackIds.clear();
    }

    // --- Standard Methods ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Playlist playlist = (Playlist) o;
        return Objects.equals(id, playlist.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Playlist{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", trackCount=" + trackIds.size() +
                '}';
    }
}
