package com.musiclibrary.model;

import com.musiclibrary.i18n.I18nManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Domain entity representing an Artist aggregating tracks and albums by that artist.
 */
public class Artist {

    private final String name;
    private final int albumCount;
    private final int trackCount;
    private final long totalDurationSeconds;
    private final Track representativeTrack;
    private final List<Track> tracks;

    public Artist(String name,
                  int albumCount,
                  int trackCount,
                  long totalDurationSeconds,
                  Track representativeTrack,
                  List<Track> tracks) {
        this.name = name != null ? name : "";
        this.albumCount = Math.max(0, albumCount);
        this.trackCount = Math.max(0, trackCount);
        this.totalDurationSeconds = Math.max(0, totalDurationSeconds);
        this.representativeTrack = representativeTrack;
        this.tracks = tracks != null ? List.copyOf(tracks) : List.of();
    }

    public String getName() {
        return name;
    }

    public int getAlbumCount() {
        return albumCount;
    }

    public int getTrackCount() {
        return trackCount;
    }

    public long getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public Track getRepresentativeTrack() {
        return representativeTrack;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    /**
     * Checks if this artist has embedded or accessible artwork on its representative track.
     */
    public boolean hasArtwork() {
        return representativeTrack != null && representativeTrack.hasArtwork();
    }

    /**
     * Groups tracks into sorted Artist entities.
     *
     * @param tracks collection of tracks (may be empty or null)
     * @return sorted list of aggregated Artist entities
     */
    public static List<Artist> fromTracks(Collection<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }

        String unknownArtistText = I18nManager.getInstance().get("album.unknown_artist");
        if (unknownArtistText == null || unknownArtistText.isBlank() || unknownArtistText.startsWith("!")) {
            unknownArtistText = "Unknown Artist";
        }

        Map<String, List<Track>> grouped = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();

        for (Track track : tracks) {
            if (track == null) {
                continue;
            }
            String rawArtist = (track.getArtist() != null && !track.getArtist().isBlank())
                    ? track.getArtist().trim()
                    : unknownArtistText;
            String key = rawArtist.toLowerCase(Locale.ROOT);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(track);
            displayNames.putIfAbsent(key, rawArtist);
        }

        List<Artist> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<Track>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            String displayName = displayNames.get(key);
            List<Track> artistTracks = entry.getValue();
            result.add(createArtist(displayName, artistTracks));
        }

        result.sort(Comparator.comparing(Artist::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static Artist createArtist(String name, List<Track> artistTracks) {
        artistTracks.sort(Comparator
                .comparing((Track t) -> t.getAlbum() != null ? t.getAlbum().trim() : "", String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Track::getTrackNumber)
                .thenComparing(t -> t.getTitle() != null ? t.getTitle() : "", String.CASE_INSENSITIVE_ORDER));

        Set<String> uniqueAlbums = new HashSet<>();
        long totalDuration = 0;
        Track repTrack = null;

        for (Track t : artistTracks) {
            String alb = t.getAlbum() != null ? t.getAlbum().trim() : "";
            if (!alb.isEmpty()) {
                uniqueAlbums.add(alb.toLowerCase(Locale.ROOT));
            }
            totalDuration += t.getDurationSeconds();
            if (repTrack == null && t.hasArtwork()) {
                repTrack = t;
            }
        }

        if (repTrack == null && !artistTracks.isEmpty()) {
            repTrack = artistTracks.get(0);
        }

        int albumCount = uniqueAlbums.isEmpty()
                ? (artistTracks.isEmpty() ? 0 : 1)
                : uniqueAlbums.size();

        return new Artist(
                name,
                albumCount,
                artistTracks.size(),
                totalDuration,
                repTrack,
                artistTracks
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artist artist = (Artist) o;
        return name.equalsIgnoreCase(artist.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return "Artist{" +
                "name='" + name + '\'' +
                ", albumCount=" + albumCount +
                ", trackCount=" + trackCount +
                ", totalDurationSeconds=" + totalDurationSeconds +
                '}';
    }
}
