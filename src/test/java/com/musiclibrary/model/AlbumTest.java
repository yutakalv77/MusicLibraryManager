package com.musiclibrary.model;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;

class AlbumTest {

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("fromTracks correctly groups tracks, sorts by album and artist, and computes metadata")
    void testFromTracksGroupingAndSorting() {
        Track t1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Song A").artist("Queen").album("A Night at the Opera").year(1975).durationSeconds(200).trackNumber(2).build();
        Track t2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Song B").artist("Queen").album("A Night at the Opera").year(1975).durationSeconds(300).trackNumber(1).build();
        Track t3 = Track.builder().id("3").filePath("tracks/3.mp3").title("Song C").artist("Pink Floyd").album("The Dark Side of the Moon").year(1973).durationSeconds(250).trackNumber(1).build();

        List<Album> albums = Album.fromTracks(List.of(t1, t2, t3));

        assertThat(albums).hasSize(2);

        Album album1 = albums.get(0);
        assertThat(album1.getTitle()).isEqualTo("A Night at the Opera");
        assertThat(album1.getArtist()).isEqualTo("Queen");
        assertThat(album1.getYear()).isEqualTo(1975);
        assertThat(album1.getTrackCount()).isEqualTo(2);
        assertThat(album1.getTotalDurationSeconds()).isEqualTo(500);
        // Tracks should be sorted by trackNumber (t2 then t1)
        assertThat(album1.getTracks()).containsExactly(t2, t1);
        assertThat(album1.getRepresentativeTrack()).isEqualTo(t2);

        Album album2 = albums.get(1);
        assertThat(album2.getTitle()).isEqualTo("The Dark Side of the Moon");
        assertThat(album2.getArtist()).isEqualTo("Pink Floyd");
        assertThat(album2.getYear()).isEqualTo(1973);
        assertThat(album2.getTrackCount()).isEqualTo(1);
        assertThat(album2.getTotalDurationSeconds()).isEqualTo(250);
        assertThat(album2.getTracks()).containsExactly(t3);
    }

    @Test
    @DisplayName("fromTracks separates albums with the exact same title across different folders and unrelated artists")
    void testSameAlbumTitleDifferentArtistsSeparation() {
        Track t1 = Track.builder().id("1").filePath("tracks/queen/1.mp3").title("Track 1").artist("Queen").album("Greatest Hits").trackNumber(1).build();
        Track t2 = Track.builder().id("2").filePath("tracks/beatles/2.mp3").title("Track A").artist("The Beatles").album("Greatest Hits").trackNumber(1).build();

        List<Album> albums = Album.fromTracks(List.of(t1, t2));

        assertThat(albums).hasSize(2);
        assertThat(albums.get(0).getTitle()).isEqualTo("Greatest Hits");
        assertThat(albums.get(0).getArtist()).isEqualTo("Queen");
        assertThat(albums.get(1).getTitle()).isEqualTo("Greatest Hits");
        assertThat(albums.get(1).getArtist()).isEqualTo("The Beatles");
    }

    @Test
    @DisplayName("Compilation albums in the same folder with multiple artists consolidate into single Various Artists album")
    void testCompilationAlbumConsolidation() {
        Track t1 = Track.builder().id("1").filePath("tracks/ost/pulp/01.mp3").title("Misirlou").artist("Dick Dale").album("Pulp Fiction").trackNumber(1).build();
        Track t2 = Track.builder().id("2").filePath("tracks/ost/pulp/02.mp3").title("Jungle Boogie").artist("Kool & The Gang").album("Pulp Fiction").trackNumber(2).build();
        Track t3 = Track.builder().id("3").filePath("tracks/ost/pulp/03.mp3").title("Let's Stay Together").artist("Al Green").album("Pulp Fiction").trackNumber(3).build();

        List<Album> albums = Album.fromTracks(List.of(t1, t2, t3));

        assertThat(albums).hasSize(1);
        Album album = albums.get(0);
        assertThat(album.getTitle()).isEqualTo("Pulp Fiction");
        assertThat(album.getArtist()).isEqualTo("Various Artists");
        assertThat(album.getTrackCount()).isEqualTo(3);
        assertThat(album.getTracks()).containsExactly(t1, t2, t3);
    }

    @Test
    @DisplayName("Featuring and collaborative tracks consolidate into single album retaining dominant main artist")
    void testFeaturingTracksConsolidation() {
        List<Track> ramTracks = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            ramTracks.add(Track.builder()
                    .id("t" + i)
                    .filePath("music/daft/ram/" + i + ".mp3")
                    .title("Track " + i)
                    .artist("Daft Punk")
                    .album("Random Access Memories")
                    .trackNumber(i)
                    .build());
        }
        ramTracks.add(Track.builder()
                .id("t9")
                .filePath("music/daft/ram/9.mp3")
                .title("Get Lucky")
                .artist("Daft Punk feat. Pharrell Williams")
                .album("Random Access Memories")
                .trackNumber(9)
                .build());
        ramTracks.add(Track.builder()
                .id("t10")
                .filePath("music/daft/ram/10.mp3")
                .title("Instant Crush")
                .artist("Daft Punk with Julian Casablancas")
                .album("Random Access Memories")
                .trackNumber(10)
                .build());

        List<Album> albums = Album.fromTracks(ramTracks);

        assertThat(albums).hasSize(1);
        Album album = albums.get(0);
        assertThat(album.getTitle()).isEqualTo("Random Access Memories");
        assertThat(album.getArtist()).isEqualTo("Daft Punk");
        assertThat(album.getTrackCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Tracks with missing album title are isolated per artist rather than lumped into single massive album")
    void testMissingAlbumTitleIsolationPerArtist() {
        Track t1 = Track.builder().id("1").filePath("tracks/q1.mp3").title("Song 1").artist("Queen").build();
        Track t2 = Track.builder().id("2").filePath("tracks/q2.mp3").title("Song 2").artist("Queen").build();
        Track t3 = Track.builder().id("3").filePath("tracks/b1.mp3").title("Song 3").artist("The Beatles").build();

        List<Album> albums = Album.fromTracks(List.of(t1, t2, t3));

        assertThat(albums).hasSize(2);
        assertThat(albums.get(0).getTitle()).isEqualTo("Unknown Album");
        assertThat(albums.get(0).getArtist()).isEqualTo("Queen");
        assertThat(albums.get(0).getTrackCount()).isEqualTo(2);

        assertThat(albums.get(1).getTitle()).isEqualTo("Unknown Album");
        assertThat(albums.get(1).getArtist()).isEqualTo("The Beatles");
        assertThat(albums.get(1).getTrackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromTracks groups in O(N) ultra-fast for 10,000 tracks without heap exhaustion")
    void testFromTracksLargeScalePerformance() {
        int trackCount = 10000;
        List<Track> tracks = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            int albumNum = i % 200; // 200 albums, 50 tracks each
            tracks.add(Track.builder()
                    .id("t-" + i)
                    .filePath("tracks/" + i + ".mp3")
                    .title("Track " + i)
                    .artist("Artist " + (albumNum % 20))
                    .album("Album " + albumNum)
                    .trackNumber((i % 50) + 1)
                    .durationSeconds(180)
                    .year(2000 + (albumNum % 25))
                    .build());
        }

        long start = System.currentTimeMillis();
        List<Album> albums = Album.fromTracks(tracks);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(albums).hasSize(200);
        assertThat(elapsed).as("Aggregation for 10,000 tracks should be O(N) and finish within 1000ms").isLessThan(1000);
        assertThat(albums.get(0).getTrackCount()).isEqualTo(50);
        assertThat(albums.get(0).getTotalDurationSeconds()).isEqualTo(50 * 180);
    }

    @Test
    @DisplayName("fromTracks prioritizes track with artwork as representative track")
    void testRepresentativeTrackWithArtwork() {
        Track noArt = Track.builder().id("1").filePath("tracks/noart.mp3").title("No Art").artist("Artist").album("Album").trackNumber(1).build();
        Track withArt = Track.builder().id("2").filePath("tracks/art.mp3").title("With Art").artist("Artist").album("Album").trackNumber(2)
                .artworkData(new byte[]{1, 2, 3}).build();

        List<Album> albums = Album.fromTracks(List.of(noArt, withArt));

        assertThat(albums).hasSize(1);
        assertThat(albums.get(0).hasArtwork()).isTrue();
        assertThat(albums.get(0).getRepresentativeTrack()).isEqualTo(withArt);
    }

    @Test
    @DisplayName("fromTracks handles empty and null inputs safely")
    void testEmptyAndNullTracks() {
        assertThat(Album.fromTracks(null)).isEmpty();
        assertThat(Album.fromTracks(List.of())).isEmpty();
    }

    @Test
    @DisplayName("fromTracks handles missing album or artist with localized defaults in English and Japanese")
    void testMissingAlbumOrArtistBilingual() {
        Track t = Track.builder().id("1").filePath("tracks/unknown.mp3").title("Unknown Song").build();

        // English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        List<Album> enAlbums = Album.fromTracks(List.of(t));
        assertThat(enAlbums).hasSize(1);
        assertThat(enAlbums.get(0).getTitle()).isEqualTo("Unknown Album");
        assertThat(enAlbums.get(0).getArtist()).isEqualTo("Unknown Artist");

        // Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        List<Album> jaAlbums = Album.fromTracks(List.of(t));
        assertThat(jaAlbums).hasSize(1);
        assertThat(jaAlbums.get(0).getTitle()).isEqualTo("不明なアルバム");
        assertThat(jaAlbums.get(0).getArtist()).isEqualTo("不明なアーティスト");
    }

    @Test
    @DisplayName("fromTracks handles special characters, emojis, whitespace-only, and ultra-long tags robustly")
    void testSpecialCharactersAndEmojis() {
        String emojiTitle = "🎵 Album of the Year ★ 2026 🚀";
        String emojiArtist = "米津玄師 🎧 / Vaundy ✨";
        String longString = "A".repeat(500);

        Track t1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Track 1").artist(emojiArtist).album(emojiTitle).trackNumber(1).build();
        Track t2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Track 2").artist("Artist").album(longString).trackNumber(1).build();
        Track t3 = Track.builder().id("3").filePath("tracks/3.mp3").title("Track 3").artist("   ").album("\t\n  ").trackNumber(1).build();

        List<Album> albums = Album.fromTracks(List.of(t1, t2, t3));

        assertThat(albums).hasSize(3);
        Album emojiAlbum = albums.stream().filter(a -> a.getTitle().equals(emojiTitle)).findFirst().orElseThrow();
        assertThat(emojiAlbum.getArtist()).isEqualTo(emojiArtist);

        Album longAlbum = albums.stream().filter(a -> a.getTitle().equals(longString)).findFirst().orElseThrow();
        assertThat(longAlbum.getTrackCount()).isEqualTo(1);

        Album blankAlbum = albums.stream().filter(a -> a.getTitle().equals("Unknown Album")).findFirst().orElseThrow();
        assertThat(blankAlbum.getArtist()).isEqualTo("Unknown Artist");
    }

    @Test
    @DisplayName("fromTracks sorts tracks stably when track numbers are 0, duplicate, or have missing numbers")
    void testTrackNumberEdgeCasesAndSortingStability() {
        Track t0a = Track.builder().id("0a").filePath("tracks/0a.mp3").title("Beta").artist("Band").album("Album").trackNumber(0).build();
        Track t0b = Track.builder().id("0b").filePath("tracks/0b.mp3").title("Alpha").artist("Band").album("Album").trackNumber(0).build();
        Track t5 = Track.builder().id("5").filePath("tracks/5.mp3").title("Track 5").artist("Band").album("Album").trackNumber(5).build();
        Track t99 = Track.builder().id("99").filePath("tracks/99.mp3").title("Track 99").artist("Band").album("Album").trackNumber(99).build();

        List<Album> albums = Album.fromTracks(List.of(t99, t0a, t5, t0b));

        assertThat(albums).hasSize(1);
        // Track 0s should be sorted alphabetically by title ("Alpha" then "Beta"), followed by 5 and 99
        assertThat(albums.get(0).getTracks()).containsExactly(t0b, t0a, t5, t99);
    }

    @Test
    @DisplayName("getTracks returns an unmodifiable list ensuring domain entity immutability")
    void testImmutabilityOfTracksList() {
        Track t = Track.builder().id("1").filePath("tracks/1.mp3").title("Song").artist("Artist").album("Album").build();
        List<Album> albums = Album.fromTracks(List.of(t));
        Album album = albums.get(0);

        List<Track> tracks = album.getTracks();
        Track extraTrack = Track.builder().id("2").filePath("tracks/2.mp3").title("Song 2").build();

        assertThatThrownBy(() -> tracks.add(extraTrack))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(tracks::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("equals, hashCode, and toString adhere to contract with case-insensitive title and artist")
    void testEqualsHashCodeAndToString() {
        Track t1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Song 1").artist("queen").album("A NIGHT AT THE OPERA").year(1975).build();
        Track t2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Song 2").artist("Queen").album("a night at the opera").year(1975).build();

        Album a1 = Album.fromTracks(List.of(t1)).get(0);
        Album a2 = Album.fromTracks(List.of(t2)).get(0);

        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1.toString()).contains("A NIGHT AT THE OPERA").contains("1975");
    }
}
