package com.musiclibrary.model;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistTest {

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("fromTracks returns empty list for null or empty input")
    void testFromTracksEmptyAndNull() {
        assertThat(Artist.fromTracks(null)).isEmpty();
        assertThat(Artist.fromTracks(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("fromTracks aggregates tracks by artist case-insensitively, counts unique albums, and sorts tracks")
    void testFromTracksAggregationAndSorting() {
        Track q2 = Track.builder()
                .id("1")
                .filePath("1.mp3")
                .title("Love of My Life")
                .artist("Queen")
                .album("A Night at the Opera")
                .trackNumber(2)
                .durationSeconds(219)
                .build();
        Track q1 = Track.builder()
                .id("2")
                .filePath("2.mp3")
                .title("Bohemian Rhapsody")
                .artist("queen")
                .album("A Night at the Opera")
                .trackNumber(1)
                .durationSeconds(354)
                .hasArtwork(true)
                .build();
        Track q3 = Track.builder()
                .id("3")
                .filePath("3.mp3")
                .title("We Will Rock You")
                .artist("Queen")
                .album("News of the World")
                .trackNumber(1)
                .durationSeconds(122)
                .build();
        Track pf = Track.builder()
                .id("4")
                .filePath("4.mp3")
                .title("Time")
                .artist("Pink Floyd")
                .album("The Dark Side of the Moon")
                .trackNumber(4)
                .durationSeconds(425)
                .build();

        List<Artist> artists = Artist.fromTracks(Arrays.asList(q2, null, q1, q3, pf));

        assertThat(artists).hasSize(2);
        // Sorted alphabetically by artist name: Pink Floyd, then Queen
        Artist pinkFloyd = artists.get(0);
        Artist queen = artists.get(1);

        assertThat(pinkFloyd.getName()).isEqualTo("Pink Floyd");
        assertThat(pinkFloyd.getAlbumCount()).isEqualTo(1);
        assertThat(pinkFloyd.getTrackCount()).isEqualTo(1);
        assertThat(pinkFloyd.getTotalDurationSeconds()).isEqualTo(425);
        assertThat(pinkFloyd.hasArtwork()).isFalse();

        assertThat(queen.getName()).isEqualTo("Queen");
        assertThat(queen.getAlbumCount()).isEqualTo(2);
        assertThat(queen.getTrackCount()).isEqualTo(3);
        assertThat(queen.getTotalDurationSeconds()).isEqualTo(219 + 354 + 122);
        assertThat(queen.hasArtwork()).isTrue();
        assertThat(queen.getRepresentativeTrack()).isEqualTo(q1);
        // Tracks sorted by Album ("A Night at the Opera" track 1, track 2, then "News of the World" track 1)
        assertThat(queen.getTracks()).containsExactly(q1, q2, q3);
    }

    @Test
    @DisplayName("fromTracks assigns localized Unknown Artist for blank or null artist names")
    void testUnknownArtistFallback() {
        Track unknown = Track.builder().id("u").filePath("u.mp3").title("Mystery").artist("   ").album("").build();

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        List<Artist> enArtists = Artist.fromTracks(List.of(unknown));
        assertThat(enArtists).hasSize(1);
        assertThat(enArtists.get(0).getName()).isEqualTo("Unknown Artist");
        assertThat(enArtists.get(0).getAlbumCount()).isEqualTo(1);

        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        List<Artist> jaArtists = Artist.fromTracks(List.of(unknown));
        assertThat(jaArtists).hasSize(1);
        assertThat(jaArtists.get(0).getName()).isEqualTo("不明なアーティスト");
    }

    @Test
    @DisplayName("equals, hashCode, toString, and constructor boundaries work properly")
    void testEqualsHashCodeAndBoundaries() {
        Artist a1 = new Artist("Queen", 2, 10, 2000, null, null);
        Artist a2 = new Artist("QUEEN", 5, 20, 4000, null, List.of());
        Artist diff = new Artist("Bowie", -1, -5, -100, null, null);

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1).isNotEqualTo(diff);
        assertThat(a1.toString()).contains("Queen");

        assertThat(diff.getAlbumCount()).isZero();
        assertThat(diff.getTrackCount()).isZero();
        assertThat(diff.getTotalDurationSeconds()).isZero();
        assertThat(diff.getTracks()).isEmpty();
    }
}
