package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Artist;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtistCardPanelTest {

    private Artist multiAlbumArtist;
    private Artist singleAlbumTrackArtist;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        Track t1 = Track.builder().id("1").filePath("1.mp3").title("Song 1").artist("Queen").album("Album A").durationSeconds(200).build();
        Track t2 = Track.builder().id("2").filePath("2.mp3").title("Song 2").artist("Queen").album("Album B").durationSeconds(250).build();
        multiAlbumArtist = Artist.fromTracks(List.of(t1, t2)).get(0);

        Track single = Track.builder().id("s").filePath("s.mp3").title("Solo").artist("Solo Artist").album("Single Album").durationSeconds(180).build();
        singleAlbumTrackArtist = Artist.fromTracks(List.of(single)).get(0);
    }

    @Test
    @DisplayName("Constructor throws NullPointerException when artist is null")
    void testConstructorNullCheck() {
        assertThatThrownBy(() -> new ArtistCardPanel(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Card displays correct artist name, album count, and track count")
    void testCardMetadataDisplay() {
        ArtistCardPanel card = new ArtistCardPanel(multiAlbumArtist);

        assertThat(card.getArtist()).isEqualTo(multiAlbumArtist);
        assertThat(card.getPreferredSize().width).isEqualTo(ArtistCardPanel.CARD_WIDTH);
        assertThat(card.getPreferredSize().height).isEqualTo(ArtistCardPanel.CARD_HEIGHT);

        assertThat(card.getTitleLabel().getText()).isEqualTo("Queen");
        assertThat(card.getAlbumCountLabel().getText()).isEqualTo("2 albums");
        assertThat(card.getMetaLabel().getText()).isEqualTo("2 songs");
    }

    @Test
    @DisplayName("Single album and single track artist shows singular forms")
    void testSingularFormats() {
        ArtistCardPanel card = new ArtistCardPanel(singleAlbumTrackArtist);

        assertThat(card.getAlbumCountLabel().getText()).isEqualTo("1 album");
        assertThat(card.getMetaLabel().getText()).isEqualTo("1 song");
    }

    @Test
    @DisplayName("Clicking card triggers onArtistSelected callback")
    void testClickTriggersSelection() {
        AtomicReference<Artist> selected = new AtomicReference<>();
        ArtistCardPanel card = new ArtistCardPanel(multiAlbumArtist, selected::set);

        MouseEvent clickEvent = new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false);
        for (var ml : card.getMouseListeners()) {
            ml.mouseClicked(clickEvent);
        }
        assertThat(selected.get()).isEqualTo(multiAlbumArtist);

        AtomicReference<Artist> secondRef = new AtomicReference<>();
        card.setOnArtistSelected(secondRef::set);
        for (var ml : card.getMouseListeners()) {
            ml.mouseClicked(clickEvent);
        }
        assertThat(secondRef.get()).isEqualTo(multiAlbumArtist);
    }

    @Test
    @DisplayName("Selection and hover states render cleanly without exceptions")
    void testSelectionHoverAndPaint() {
        ArtistCardPanel card = new ArtistCardPanel(multiAlbumArtist);
        card.setSize(ArtistCardPanel.CARD_WIDTH, ArtistCardPanel.CARD_HEIGHT);

        assertThat(card.isSelected()).isFalse();
        card.setSelected(true);
        assertThat(card.isSelected()).isTrue();

        BufferedImage img = new BufferedImage(ArtistCardPanel.CARD_WIDTH, ArtistCardPanel.CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        card.paint(g2);

        card.setSelected(false);
        MouseEvent enterEvent = new MouseEvent(card, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 10, 10, 0, false);
        MouseEvent exitEvent = new MouseEvent(card, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, 10, 10, 0, false);
        for (var ml : card.getMouseListeners()) {
            ml.mouseEntered(enterEvent);
        }
        card.paint(g2);
        for (var ml : card.getMouseListeners()) {
            ml.mouseExited(exitEvent);
        }
        g2.dispose();
    }

    @Test
    @DisplayName("updateLanguage updates album count and track count labels between English and Japanese")
    void testUpdateLanguage() {
        ArtistCardPanel card = new ArtistCardPanel(multiAlbumArtist);

        assertThat(card.getAlbumCountLabel().getText()).contains("albums");
        assertThat(card.getMetaLabel().getText()).contains("songs");

        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        card.updateLanguage();
        assertThat(card.getAlbumCountLabel().getText()).contains("アルバム");
        assertThat(card.getMetaLabel().getText()).contains("曲");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        card.updateLanguage();
        assertThat(card.getAlbumCountLabel().getText()).contains("albums");
        assertThat(card.getMetaLabel().getText()).contains("songs");

        card.dispose();
        card.dispose();
    }
}
