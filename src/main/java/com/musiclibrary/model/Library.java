package com.musiclibrary.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Domain entity representing the entire music library.
 * Manages tracks and playlists using thread-safe collections with O(1) lookup indices.
 */
public class Library {

    private final Map<String, Track> tracks;
    private final List<Playlist> playlists;

    // Fast O(1) lookup index for file paths
    private final Map<String, String> filePathToIdIndex;

    // Fast O(1) lookup index for content duplicates
    private record ContentKey(long durationSeconds, long fileSizeBytes, String titleNorm, String artistNorm) {}
    private final Map<ContentKey, Set<String>> contentDuplicateIndex;

    /**
     * Initializes an empty library with thread-safe backing collections.
     */
    public Library() {
        this.tracks = new ConcurrentHashMap<>();
        this.playlists = new CopyOnWriteArrayList<>();
        this.filePathToIdIndex = new ConcurrentHashMap<>();
        this.contentDuplicateIndex = new ConcurrentHashMap<>();
    }

    /**
     * Initializes a library with existing tracks and playlists.
     *
     * @param initialTracks    collection of tracks to add
     * @param initialPlaylists collection of playlists to add
     */
    public Library(Collection<Track> initialTracks, Collection<Playlist> initialPlaylists) {
        this.tracks = new ConcurrentHashMap<>();
        this.playlists = new CopyOnWriteArrayList<>();
        this.filePathToIdIndex = new ConcurrentHashMap<>();
        this.contentDuplicateIndex = new ConcurrentHashMap<>();
        if (initialTracks != null) {
            addTracks(initialTracks);
        }
        if (initialPlaylists != null) {
            for (Playlist playlist : initialPlaylists) {
                if (playlist != null) {
                    addPlaylist(playlist);
                }
            }
        }
    }

    // --- Index Helper Methods ---

    private static String normalizeFilePathKey(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static ContentKey createContentKey(Track track) {
        if (track == null) {
            return null;
        }
        String titleNorm = track.getTitle() != null ? track.getTitle().toLowerCase(Locale.ROOT).trim() : "";
        String artistNorm = track.getArtist() != null ? track.getArtist().toLowerCase(Locale.ROOT).trim() : "";
        if (titleNorm.isEmpty() && artistNorm.isEmpty()) {
            return null;
        }
        return new ContentKey(track.getDurationSeconds(), track.getFileSizeBytes(), titleNorm, artistNorm);
    }

    private void indexTrack(Track track) {
        if (track == null) {
            return;
        }
        String pathKey = normalizeFilePathKey(track.getFilePath());
        if (pathKey != null && track.getId() != null) {
            filePathToIdIndex.put(pathKey, track.getId());
        }
        ContentKey contentKey = createContentKey(track);
        if (contentKey != null && track.getId() != null) {
            contentDuplicateIndex.computeIfAbsent(contentKey, k -> ConcurrentHashMap.newKeySet()).add(track.getId());
        }
    }

    private void unindexTrack(Track track) {
        if (track == null) {
            return;
        }
        String pathKey = normalizeFilePathKey(track.getFilePath());
        if (pathKey != null && track.getId() != null) {
            filePathToIdIndex.remove(pathKey, track.getId());
        }
        ContentKey contentKey = createContentKey(track);
        if (contentKey != null && track.getId() != null) {
            Set<String> set = contentDuplicateIndex.get(contentKey);
            if (set != null) {
                set.remove(track.getId());
                if (set.isEmpty()) {
                    contentDuplicateIndex.remove(contentKey, Collections.emptySet());
                }
            }
        }
    }

    // --- Track Operations ---

    /**
     * Adds or updates a track in the library.
     *
     * @param track track to add
     * @throws IllegalArgumentException if track is null or has empty ID
     */
    public void addTrack(Track track) {
        if (track == null) {
            throw new IllegalArgumentException("Track cannot be null");
        }
        if (track.getId() == null || track.getId().isBlank()) {
            throw new IllegalArgumentException("Track id cannot be null or blank");
        }
        Track prev = tracks.put(track.getId(), track);
        if (prev != null) {
            unindexTrack(prev);
        }
        indexTrack(track);
    }

    /**
     * Adds multiple tracks to the library.
     *
     * @param trackCollection collection of tracks to add
     */
    public void addTracks(Collection<Track> trackCollection) {
        if (trackCollection == null) {
            return;
        }
        for (Track track : trackCollection) {
            if (track != null) {
                addTrack(track);
            }
        }
    }

    /**
     * Removes a track from the library and cleans up all references to this track
     * across all playlists.
     *
     * @param trackId unique ID of the track to remove
     * @return the removed Track, or null if it was not in the library
     */
    public Track removeTrack(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return null;
        }

        Track removed = tracks.remove(trackId);
        if (removed != null) {
            unindexTrack(removed);
        }

        // Cascade cleanup in all playlists (remove all occurrences if duplicates exist)
        for (Playlist playlist : playlists) {
            while (playlist.removeTrack(trackId)) {
                // Remove all duplicate occurrences of this track ID
            }
        }

        return removed;
    }

    /**
     * Retrieves a track by its unique ID.
     *
     * @param trackId unique ID of the track
     * @return Track, or null if not found
     */
    public Track getTrack(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return null;
        }
        return tracks.get(trackId);
    }

    /**
     * Checks if a track exists in the library.
     *
     * @param trackId unique ID of the track
     * @return true if present, false otherwise
     */
    public boolean containsTrack(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return false;
        }
        return tracks.containsKey(trackId);
    }

    /**
     * Returns an unmodifiable collection of all tracks.
     *
     * @return unmodifiable collection of tracks
     */
    public Collection<Track> getTracks() {
        return Collections.unmodifiableCollection(tracks.values());
    }

    /**
     * Returns an unmodifiable view of the tracks map.
     *
     * @return unmodifiable map of track ID to Track
     */
    public Map<String, Track> getTrackMap() {
        return Collections.unmodifiableMap(tracks);
    }

    /**
     * Returns the total number of tracks in the library.
     *
     * @return track count
     */
    public int getTrackCount() {
        return tracks.size();
    }

    /**
     * Finds a track by its file path (case-insensitive).
     *
     * @param filePath file path to look up
     * @return Track or null if not found
     */
    public Track findByFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        String pathKey = normalizeFilePathKey(filePath);
        if (pathKey != null) {
            String trackId = filePathToIdIndex.get(pathKey);
            if (trackId != null) {
                Track t = tracks.get(trackId);
                if (t != null) {
                    return t;
                }
            }
        }
        // Fallback linear scan if not found in normalized index
        for (Track t : tracks.values()) {
            if (filePath.equalsIgnoreCase(t.getFilePath())) {
                return t;
            }
        }
        return null;
    }

    /**
     * Upserts a track: if an existing track matches by ID or file path,
     * updates its metadata while preserving user statistics (id, rating, playCount, dateAdded).
     * Otherwise adds the track as new.
     *
     * @param track track with new metadata
     * @return persisted Track instance
     */
    public Track upsertTrack(Track track) {
        if (track == null) {
            throw new IllegalArgumentException("Track cannot be null");
        }

        Track existing = null;
        if (track.getId() != null && tracks.containsKey(track.getId())) {
            existing = tracks.get(track.getId());
        } else if (track.getFilePath() != null && !track.getFilePath().isBlank()) {
            existing = findByFilePath(track.getFilePath());
        }

        if (existing != null) {
            Track updated = track.toBuilder()
                    .id(existing.getId())
                    .playCount(existing.getPlayCount())
                    .rating(existing.getRating())
                    .dateAdded(existing.getDateAdded())
                    .build();
            addTrack(updated);
            return updated;
        } else {
            addTrack(track);
            return track;
        }
    }

    /**
     * Finds an existing track with identical content (title, artist, duration, file size)
     * at a different file path.
     *
     * @param track candidate track
     * @return duplicate Track if found, null otherwise
     */
    public Track findContentDuplicate(Track track) {
        if (track == null) {
            return null;
        }

        ContentKey contentKey = createContentKey(track);
        if (contentKey == null) {
            return null;
        }

        Set<String> candidateIds = contentDuplicateIndex.get(contentKey);
        if (candidateIds == null || candidateIds.isEmpty()) {
            return null;
        }

        String trackPathKey = normalizeFilePathKey(track.getFilePath());

        for (String candidateId : candidateIds) {
            if (Objects.equals(candidateId, track.getId())) {
                continue;
            }
            Track t = tracks.get(candidateId);
            if (t == null) {
                continue;
            }
            if (trackPathKey != null && trackPathKey.equals(normalizeFilePathKey(t.getFilePath()))) {
                continue;
            }
            return t;
        }
        return null;
    }

    /**
     * Checks if a track with identical content exists in the library.
     *
     * @param track candidate track
     * @return true if duplicate exists, false otherwise
     */
    public boolean isContentDuplicate(Track track) {
        return findContentDuplicate(track) != null;
    }

    // --- Playlist Operations ---

    /**
     * Creates a new playlist with the specified name and adds it to the library.
     *
     * @param name playlist name
     * @return created playlist
     */
    public Playlist createPlaylist(String name) {
        Playlist playlist = new Playlist(name);
        addPlaylist(playlist);
        return playlist;
    }

    /**
     * Adds a playlist to the library. If a playlist with the same ID already exists,
     * it will be replaced.
     *
     * @param playlist playlist to add
     * @throws IllegalArgumentException if playlist is null or has empty ID
     */
    public void addPlaylist(Playlist playlist) {
        if (playlist == null) {
            throw new IllegalArgumentException("Playlist cannot be null");
        }
        if (playlist.getId() == null || playlist.getId().isBlank()) {
            throw new IllegalArgumentException("Playlist id cannot be null or blank");
        }

        // Replace if exists, otherwise add
        deletePlaylist(playlist.getId());
        playlists.add(playlist);
    }

    /**
     * Deletes a playlist by its unique ID.
     *
     * @param playlistId unique ID of the playlist to remove
     * @return true if removed, false if not found
     */
    public boolean deletePlaylist(String playlistId) {
        if (playlistId == null || playlistId.isBlank()) {
            return false;
        }
        return playlists.removeIf(p -> Objects.equals(p.getId(), playlistId));
    }

    /**
     * Retrieves a playlist by its unique ID.
     *
     * @param playlistId unique ID of the playlist
     * @return Playlist, or null if not found
     */
    public Playlist getPlaylist(String playlistId) {
        if (playlistId == null || playlistId.isBlank()) {
            return null;
        }
        for (Playlist playlist : playlists) {
            if (Objects.equals(playlist.getId(), playlistId)) {
                return playlist;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable list of all playlists.
     *
     * @return unmodifiable list of playlists
     */
    public List<Playlist> getPlaylists() {
        return Collections.unmodifiableList(playlists);
    }

    /**
     * Returns the total number of playlists.
     *
     * @return playlist count
     */
    public int getPlaylistCount() {
        return playlists.size();
    }

    // --- Search Helper ---

    /**
     * Searches tracks across title, artist, album, and genre with case-insensitive matching.
     * If the keyword is null or blank, all tracks in the library are returned.
     *
     * @param keyword search term
     * @return list of matching tracks
     */
    public List<Track> findTracks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>(tracks.values());
        }

        String lowerKeyword = keyword.toLowerCase(Locale.ROOT).trim();
        List<Track> matched = new ArrayList<>();

        for (Track track : tracks.values()) {
            if (matchesTrack(track, lowerKeyword)) {
                matched.add(track);
            }
        }

        return matched;
    }

    private boolean matchesTrack(Track track, String lowerKeyword) {
        if (track.getTitle() != null && track.getTitle().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return true;
        }
        if (track.getArtist() != null && track.getArtist().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return true;
        }
        if (track.getAlbum() != null && track.getAlbum().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return true;
        }
        if (track.getGenre() != null && track.getGenre().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return true;
        }
        return false;
    }

    /**
     * Clears all tracks and playlists from the library.
     */
    public void clear() {
        tracks.clear();
        filePathToIdIndex.clear();
        contentDuplicateIndex.clear();
        playlists.clear();
    }

    @Override
    public String toString() {
        return "Library{" +
                "tracksCount=" + tracks.size() +
                ", playlistsCount=" + playlists.size() +
                '}';
    }
}
