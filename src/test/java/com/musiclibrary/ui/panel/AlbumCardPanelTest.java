package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlbumCardPanelTest {

    private Album sampleAlbum;
    private Album singleTrackAlbum;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        Track t1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Song 1").artist("Queen").album("A Night at the Opera").year(1975).durationSeconds(200).build();
        Track t2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Song 2").artist("Queen").album("A Night at the Opera").year(1975).durationSeconds(250).build();
        sampleAlbum = Album.fromTracks(List.of(t1, t2)).get(0);

        Track single = Track.builder().id("s").filePath("tracks/s.mp3").title("Solo").artist("Artist").album("Single Album").year(2020).durationSeconds(180).build();
        singleTrackAlbum = Album.fromTracks(List.of(single)).get(0);
    }

    @Test
    @DisplayName("Constructor throws NullPointerException when album is null")
    void testConstructorNullCheck() {
        assertThatThrownBy(() -> new AlbumCardPanel(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Card displays correct album title, artist, year, and track count")
    void testCardMetadataDisplay() {
        AlbumCardPanel card = new AlbumCardPanel(sampleAlbum);

        assertThat(card.getAlbum()).isEqualTo(sampleAlbum);
        assertThat(card.getPreferredSize().width).isEqualTo(AlbumCardPanel.CARD_WIDTH);
        assertThat(card.getPreferredSize().height).isEqualTo(AlbumCardPanel.CARD_HEIGHT);

        // Find labels
        JPanel infoPanel = (JPanel) card.getComponent(1);
        JLabel titleLabel = (JLabel) infoPanel.getComponent(0);
        JLabel artistLabel = (JLabel) infoPanel.getComponent(1);
        JLabel metaLabel = (JLabel) infoPanel.getComponent(2);

        assertThat(titleLabel.getText()).isEqualTo("A Night at the Opera");
        assertThat(artistLabel.getText()).isEqualTo("Queen");
        assertThat(metaLabel.getText()).contains("1975").contains("2 songs");
    }

    @Test
    @DisplayName("Single track album shows singular track text format")
    void testSingleTrackFormat() {
        AlbumCardPanel card = new AlbumCardPanel(singleTrackAlbum);
        JPanel infoPanel = (JPanel) card.getComponent(1);
        JLabel metaLabel = (JLabel) infoPanel.getComponent(2);

        assertThat(metaLabel.getText()).contains("1 song").doesNotContain("1 songs");
    }

    @Test
    @DisplayName("Clicking card triggers onAlbumSelected consumer callback")
    void testClickTriggersSelection() {
        AtomicReference<Album> selected = new AtomicReference<>();
        AlbumCardPanel card = new AlbumCardPanel(sampleAlbum, selected::set);

        MouseEvent clickEvent = new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false);
        for (var ml : card.getMouseListeners()) {
            ml.mouseClicked(clickEvent);
        }

        assertThat(selected.get()).isEqualTo(sampleAlbum);

        // Also test setOnAlbumSelected
        AtomicReference<Album> secondRef = new AtomicReference<>();
        card.setOnAlbumSelected(secondRef::set);
        for (var ml : card.getMouseListeners()) {
            ml.mouseClicked(clickEvent);
        }
        assertThat(secondRef.get()).isEqualTo(sampleAlbum);
    }

    @Test
    @DisplayName("Hover events trigger repaint without throwing exceptions")
    void testHoverInteractions() {
        AlbumCardPanel card = new AlbumCardPanel(sampleAlbum);

        MouseEvent enterEvent = new MouseEvent(card, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 10, 10, 0, false);
        MouseEvent exitEvent = new MouseEvent(card, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, 10, 10, 0, false);

        for (var ml : card.getMouseListeners()) {
            ml.mouseEntered(enterEvent);
            ml.mouseExited(exitEvent);
        }
    }

    @Test
    @DisplayName("updateLanguage updates meta label seamlessly between English and Japanese")
    void testUpdateLanguage() {
        AlbumCardPanel card = new AlbumCardPanel(sampleAlbum);
        JPanel infoPanel = (JPanel) card.getComponent(1);
        JLabel metaLabel = (JLabel) infoPanel.getComponent(2);

        assertThat(metaLabel.getText()).contains("songs");

        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        card.updateLanguage();
        assertThat(metaLabel.getText()).contains("曲");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        card.updateLanguage();
        assertThat(metaLabel.getText()).contains("songs");
    }

    @Test
    @DisplayName("dispose marks card as inactive and ignores background updates cleanly")
    void testDisposeSafety() {
        AlbumCardPanel card = new AlbumCardPanel(sampleAlbum);
        card.dispose();
        // Subsequent operations or re-disposals should complete safely
        card.dispose();
        card.updateLanguage();
    }
}
