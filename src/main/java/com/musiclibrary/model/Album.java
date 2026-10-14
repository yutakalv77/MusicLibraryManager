package com.musiclibrary.model;

import com.musiclibrary.i18n.I18nManager;

import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Domain entity representing an Album aggregating multiple audio tracks.
 * Features intelligent compilation and featuring track grouping, automatic
 * primary/various artist resolution, and deterministic sorting.
 */
public class Album {

    private final String title;
    private final String artist;
    private final int year;
    private final int trackCount;
    private final long totalDurationSeconds;
    private final Track representativeTrack;
    private final List<Track> tracks;

    public Album(String title,
                 String artist,
                 int year,
                 int trackCount,
                 long totalDurationSeconds,
                 Track representativeTrack,
                 List<Track> tracks) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.year = Math.max(0, year);
        this.trackCount = Math.max(0, trackCount);
        this.totalDurationSeconds = Math.max(0, totalDurationSeconds);
        this.representativeTrack = representativeTrack;
        this.tracks = tracks != null ? List.copyOf(tracks) : List.of();
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public int getYear() {
        return year;
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
     * Checks if this album has embedded or accessible artwork.
     */
    public boolean hasArtwork() {
        return representativeTrack != null && representativeTrack.hasArtwork();
    }

    /**
     * Intelligently groups tracks into sorted Album entities.
     * Consolidates tracks of the same album (compilations, features, collaborations)
     * into a single album while isolating albums with identical titles across different folders/artists.
     *
     * @param tracks collection of tracks (may be empty or null)
     * @return sorted list of aggregated Album entities
     */
    public static List<Album> fromTracks(Collection<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }

        String unknownAlbumText = I18nManager.getInstance().get("album.unknown_album");
        if (unknownAlbumText == null || unknownAlbumText.startsWith("!")) {
            unknownAlbumText = "Unknown Album";
        }
        String unknownArtistText = I18nManager.getInstance().get("album.unknown_artist");
        if (unknownArtistText == null || unknownArtistText.startsWith("!")) {
            unknownArtistText = "Unknown Artist";
        }
        String variousArtistsText = I18nManager.getInstance().get("album.various_artists");
        if (variousArtistsText == null || variousArtistsText.isBlank() || variousArtistsText.startsWith("!")) {
            variousArtistsText = "Various Artists";
        }

        // 1. Separate tracks without album from tracks with album
        Map<String, List<Track>> unalbumedGrouped = new LinkedHashMap<>();
        Map<String, List<Track>> albumedGrouped = new LinkedHashMap<>();
        Map<String, String> albumDisplayNames = new LinkedHashMap<>();

        for (Track track : tracks) {
            if (track == null) {
                continue;
            }
            String rawAlbum = track.getAlbum() != null ? track.getAlbum().trim() : "";
            if (rawAlbum.isEmpty()) {
                String artist = (track.getArtist() != null && !track.getArtist().isBlank())
                        ? track.getArtist().trim()
                        : unknownArtistText;
                unalbumedGrouped.computeIfAbsent(artist.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(track);
            } else {
                String normAlbum = rawAlbum.toLowerCase(Locale.ROOT);
                albumedGrouped.computeIfAbsent(normAlbum, k -> new ArrayList<>()).add(track);
                albumDisplayNames.putIfAbsent(normAlbum, rawAlbum);
            }
        }

        List<Album> result = new ArrayList<>();

        // 2. Process unalbumed tracks (isolated per artist to prevent uncontrolled merge)
        for (Map.Entry<String, List<Track>> entry : unalbumedGrouped.entrySet()) {
            List<Track> unalbumedTracks = entry.getValue();
            String displayArtist = determineDisplayArtist(unalbumedTracks, unknownArtistText, variousArtistsText);
            result.add(createAlbum(unknownAlbumText, displayArtist, unalbumedTracks));
        }

        // 3. Process albumed tracks with intelligent clustering (same folder or compatible artists)
        for (Map.Entry<String, List<Track>> entry : albumedGrouped.entrySet()) {
            String normAlbum = entry.getKey();
            String displayTitle = albumDisplayNames.get(normAlbum);
            List<Track> tracksInAlbum = entry.getValue();

            List<AlbumCluster> clusters = new ArrayList<>();
            for (Track t : tracksInAlbum) {
                AlbumCluster matched = null;
                for (AlbumCluster cluster : clusters) {
                    if (cluster.matches(t)) {
                        matched = cluster;
                        break;
                    }
                }
                if (matched != null) {
                    matched.addTrack(t);
                } else {
                    clusters.add(new AlbumCluster(displayTitle, t));
                }
            }

            for (AlbumCluster cluster : clusters) {
                String displayArtist = determineDisplayArtist(cluster.tracks, unknownArtistText, variousArtistsText);
                result.add(createAlbum(cluster.albumTitle, displayArtist, cluster.tracks));
            }
        }

        // 4. Sort albums: Album title ascending, then artist ascending
        result.sort(Comparator.comparing(Album::getTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Album::getArtist, String.CASE_INSENSITIVE_ORDER));

        return result;
    }

    private static Album createAlbum(String title, String artist, List<Track> albumTracks) {
        // Sort tracks: trackNumber ascending, then title ascending
        albumTracks.sort(Comparator.comparingInt(Track::getTrackNumber)
                .thenComparing(Track::getTitle, String.CASE_INSENSITIVE_ORDER));

        int albumYear = 0;
        long totalDuration = 0;
        Track repTrack = null;

        for (Track t : albumTracks) {
            if (albumYear <= 0 && t.getYear() > 0) {
                albumYear = t.getYear();
            }
            totalDuration += t.getDurationSeconds();
            if (t.hasArtwork()) {
                if (repTrack == null || (t.getTrackNumber() == 1 && repTrack.getTrackNumber() != 1)) {
                    repTrack = t;
                }
            }
        }

        if (repTrack == null) {
            for (Track t : albumTracks) {
                if (t.getTrackNumber() == 1) {
                    repTrack = t;
                    break;
                }
            }
        }

        if (repTrack == null && !albumTracks.isEmpty()) {
            repTrack = albumTracks.get(0);
        }

        return new Album(
                title,
                artist,
                albumYear,
                albumTracks.size(),
                totalDuration,
                repTrack,
                albumTracks
        );
    }

    private static String determineDisplayArtist(List<Track> albumTracks, String unknownArtistText, String variousArtistsText) {
        if (albumTracks == null || albumTracks.isEmpty()) {
            return unknownArtistText;
        }

        // Check if all tracks have identical artist name (case-insensitive)
        String firstArtist = null;
        boolean allIdentical = true;
        for (Track t : albumTracks) {
            String a = (t.getArtist() != null && !t.getArtist().isBlank()) ? t.getArtist().trim() : unknownArtistText;
            if (firstArtist == null) {
                firstArtist = a;
            } else if (!firstArtist.equalsIgnoreCase(a)) {
                allIdentical = false;
                break;
            }
        }
        if (allIdentical && firstArtist != null) {
            return firstArtist;
        }

        // Count frequency of primary artists
        Map<String, Integer> primaryCount = new LinkedHashMap<>();
        Map<String, String> primaryOriginalCase = new LinkedHashMap<>();
        int validCount = 0;

        for (Track t : albumTracks) {
            String a = (t.getArtist() != null && !t.getArtist().isBlank()) ? t.getArtist().trim() : unknownArtistText;
            String primary = extractPrimaryArtist(a);
            String norm = primary.toLowerCase(Locale.ROOT);
            primaryCount.put(norm, primaryCount.getOrDefault(norm, 0) + 1);
            primaryOriginalCase.putIfAbsent(norm, primary);
            validCount++;
        }

        String topKey = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : primaryCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                topKey = entry.getKey();
            }
        }

        // If top primary artist is not "Various Artists" and covers >= 60% of tracks (or strict majority)
        if (topKey != null && !isVariousArtists(topKey)) {
            double ratio = (double) maxCount / validCount;
            if (ratio >= 0.60 || maxCount > validCount / 2) {
                return primaryOriginalCase.get(topKey);
            }
        }

        return variousArtistsText;
    }

    private static String extractPrimaryArtist(String rawArtist) {
        if (rawArtist == null || rawArtist.isBlank()) {
            return "";
        }
        String cleaned = rawArtist.trim();
        String lower = cleaned.toLowerCase(Locale.ROOT);

        int splitIdx = -1;
        String[] delimiters = {
                " feat. ", " feat ", " ft. ", " ft ",
                " featuring ", " with ", " vs. ", " vs ",
                " & ", " / ", " and "
        };

        for (String delim : delimiters) {
            int idx = lower.indexOf(delim);
            if (idx > 0 && (splitIdx == -1 || idx < splitIdx)) {
                splitIdx = idx;
            }
        }

        if (splitIdx > 0) {
            return cleaned.substring(0, splitIdx).trim();
        }
        return cleaned;
    }

    private static boolean isVariousArtists(String artist) {
        if (artist == null || artist.isBlank()) {
            return false;
        }
        String lower = artist.trim().toLowerCase(Locale.ROOT);
        return lower.equals("various artists")
                || lower.equals("various")
                || lower.equals("v.a.")
                || lower.equals("v.a")
                || lower.equals("va")
                || lower.equals("soundtrack")
                || lower.equals("ost");
    }

    private static boolean areArtistsCompatible(String a1, String a2) {
        if (a1 == null || a2 == null || a1.isBlank() || a2.isBlank()) {
            return false;
        }
        if (a1.equalsIgnoreCase(a2)) {
            return true;
        }
        String p1 = extractPrimaryArtist(a1).toLowerCase(Locale.ROOT);
        String p2 = extractPrimaryArtist(a2).toLowerCase(Locale.ROOT);
        if (!p1.isEmpty() && p1.equals(p2)) {
            return true;
        }
        if (a1.toLowerCase(Locale.ROOT).startsWith(p2) || a2.toLowerCase(Locale.ROOT).startsWith(p1)) {
            return true;
        }
        return false;
    }

    private static String getParentDirectory(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            Path p = Paths.get(filePath);
            Path parent = p.getParent();
            if (parent != null) {
                return parent.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }
        String normalized = filePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash > 0) {
            return normalized.substring(0, lastSlash).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static class AlbumCluster {
        final String albumTitle;
        final List<Track> tracks = new ArrayList<>();
        final Set<String> parentDirectories = new HashSet<>();
        final Set<String> artists = new HashSet<>();
        final Set<String> primaryArtists = new HashSet<>();

        AlbumCluster(String albumTitle, Track initialTrack) {
            this.albumTitle = albumTitle;
            addTrack(initialTrack);
        }

        void addTrack(Track t) {
            tracks.add(t);
            String parent = getParentDirectory(t.getFilePath());
            if (!parent.isEmpty()) {
                parentDirectories.add(parent);
            }
            String rawArtist = t.getArtist() != null ? t.getArtist().trim() : "";
            if (!rawArtist.isEmpty()) {
                String normArtist = rawArtist.toLowerCase(Locale.ROOT);
                artists.add(normArtist);
                String primary = extractPrimaryArtist(rawArtist).toLowerCase(Locale.ROOT);
                if (!primary.isEmpty()) {
                    primaryArtists.add(primary);
                }
            }
        }

        boolean matches(Track t) {
            String parent = getParentDirectory(t.getFilePath());
            // 1. Same parent directory -> unconditionally same album
            if (!parent.isEmpty() && parentDirectories.contains(parent)) {
                return true;
            }

            String rawArtist = t.getArtist() != null ? t.getArtist().trim() : "";
            String normArtist = rawArtist.toLowerCase(Locale.ROOT);
            String primary = extractPrimaryArtist(rawArtist).toLowerCase(Locale.ROOT);

            // 2. Various Artists in cluster or track
            if (isVariousArtists(normArtist) || hasVariousArtists()) {
                return true;
            }

            // 3. Exact artist match or primary artist match
            if (!normArtist.isEmpty() && artists.contains(normArtist)) {
                return true;
            }
            if (!primary.isEmpty() && primaryArtists.contains(primary)) {
                return true;
            }

            // 4. Compatible artist relations (e.g. "Queen" and "Queen & Bowie")
            for (String a : artists) {
                if (areArtistsCompatible(a, normArtist)) {
                    return true;
                }
            }

            return false;
        }

        private boolean hasVariousArtists() {
            for (String a : artists) {
                if (isVariousArtists(a)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Album album = (Album) o;
        return year == album.year &&
                Objects.equals(title, album.title) &&
                Objects.equals(artist, album.artist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title.toLowerCase(Locale.ROOT), artist.toLowerCase(Locale.ROOT), year);
    }

    @Override
    public String toString() {
        return "Album{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", year=" + year +
                ", trackCount=" + trackCount +
                ", totalDurationSeconds=" + totalDurationSeconds +
                '}';
    }
}
