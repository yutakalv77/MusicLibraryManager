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
import javax.swing.JScrollPane;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumGridViewPanelTest {

    private AlbumGridViewPanel gridPanel;
    private Album album1;
    private Album album2;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        Track t1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Song 1").artist("Queen").album("A Night at the Opera").build();
        Track t2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Song 2").artist("Pink Floyd").album("The Wall").build();

        List<Album> albums = Album.fromTracks(List.of(t1, t2));
        album1 = albums.get(0);
        album2 = albums.get(1);

        gridPanel = new AlbumGridViewPanel();
        gridPanel.setAlbums(albums);
    }

    @Test
    @DisplayName("setAlbums initializes all and displayed albums correctly")
    void testSetAlbums() {
        assertThat(gridPanel.getAllAlbums()).hasSize(2);
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(2);
    }

    @Test
    @DisplayName("filterAlbums filters by album title and artist case-insensitively")
    void testFilterAlbums() {
        gridPanel.filterAlbums("night");
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(1);
        assertThat(gridPanel.getDisplayedAlbums().get(0).getTitle()).isEqualTo("A Night at the Opera");

        gridPanel.filterAlbums("PINK");
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(1);
        assertThat(gridPanel.getDisplayedAlbums().get(0).getTitle()).isEqualTo("The Wall");

        gridPanel.filterAlbums("");
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(2);

        gridPanel.filterAlbums("Nonexistent");
        assertThat(gridPanel.getDisplayedAlbums()).isEmpty();
    }

    @Test
    @DisplayName("onAlbumSelectedAction is triggered when card is clicked")
    void testOnAlbumSelectedAction() {
        AtomicReference<Album> selectedRef = new AtomicReference<>();
        gridPanel.setOnAlbumSelectedAction(selectedRef::set);

        JScrollPane scrollPane = (JScrollPane) gridPanel.getComponent(0);
        JPanel cardsPanel = (JPanel) scrollPane.getViewport().getView();

        for (var comp : cardsPanel.getComponents()) {
            if (comp instanceof AlbumCardPanel card && card.getAlbum().equals(album1)) {
                for (var ml : card.getMouseListeners()) {
                    ml.mouseClicked(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false));
                }
            }
        }

        assertThat(selectedRef.get()).isEqualTo(album1);
    }

    @Test
    @DisplayName("updateLanguage updates texts smoothly and empty label reflects localized text")
    void testUpdateLanguage() {
        gridPanel.filterAlbums("Nonexistent");
        JScrollPane scrollPane = (JScrollPane) gridPanel.getComponent(0);
        JPanel cardsPanel = (JPanel) scrollPane.getViewport().getView();
        JLabel emptyLabel = (JLabel) cardsPanel.getComponent(0);

        assertThat(emptyLabel.getText()).isEqualTo("No albums found");

        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        gridPanel.updateLanguage();
        assertThat(emptyLabel.getText()).isEqualTo("アルバムがありません");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        gridPanel.updateLanguage();
        assertThat(emptyLabel.getText()).isEqualTo("No albums found");
    }

    @Test
    @DisplayName("setAlbums with null safely resets collections without errors")
    void testSetAlbumsNullSafety() {
        gridPanel.setAlbums(null);
        assertThat(gridPanel.getAllAlbums()).isEmpty();
        assertThat(gridPanel.getDisplayedAlbums()).isEmpty();
    }

    @Test
    @DisplayName("filterAlbums trims leading/trailing spaces and handles whitespace-only query")
    void testFilterWhitespaceHandling() {
        gridPanel.filterAlbums("   night   ");
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(1);
        assertThat(gridPanel.getDisplayedAlbums().get(0).getTitle()).isEqualTo("A Night at the Opera");

        gridPanel.filterAlbums("    ");
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(2);
    }

    @Test
    @DisplayName("Lazy chunk rendering loads albums incrementally without building thousands of cards at once")
    void testLazyChunkRendering() {
        java.util.List<Album> largeAlbumList = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            Track t = Track.builder().id("t_" + i).filePath("track_" + i + ".mp3").title("Title " + i).album("Album " + i).artist("Artist " + i).build();
            largeAlbumList.add(new Album("Album " + i, "Artist " + i, 2020, 1, 180, t, List.of(t)));
        }

        AlbumGridViewPanel chunkedPanel = new AlbumGridViewPanel(20);
        chunkedPanel.setAlbums(largeAlbumList);

        assertThat(chunkedPanel.getAllAlbums()).hasSize(150);
        assertThat(chunkedPanel.getDisplayedAlbums()).hasSize(150);
        // Only first chunk of 20 should be rendered initially
        assertThat(chunkedPanel.getRenderedCardCount()).isEqualTo(20);

        // Load next chunk
        chunkedPanel.loadNextChunk();
        assertThat(chunkedPanel.getRenderedCardCount()).isEqualTo(40);

        // Filter albums resets rendered cards to match query
        chunkedPanel.filterAlbums("Album 10");
        assertThat(chunkedPanel.getDisplayedAlbums().size()).isLessThan(150);
        assertThat(chunkedPanel.getRenderedCardCount()).isLessThanOrEqualTo(20);
    }
}
