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

    @Test
    @DisplayName("saveScrollPosition and restoreScrollPosition remember and restore vertical scroll offset")
    void testSaveAndRestoreScrollPosition() {
        java.util.List<Album> largeAlbumList = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Track t = Track.builder().id("t_" + i).filePath("track_" + i + ".mp3").title("Title " + i).album("Album " + i).artist("Artist " + i).build();
            largeAlbumList.add(new Album("Album " + i, "Artist " + i, 2020, 1, 180, t, List.of(t)));
        }

        AlbumGridViewPanel panel = new AlbumGridViewPanel(50);
        panel.setSize(400, 300);
        panel.setAlbums(largeAlbumList);
        panel.doLayout();
        panel.getScrollPane().setSize(400, 300);
        panel.getScrollPane().doLayout();

        int max = panel.getScrollPane().getVerticalScrollBar().getMaximum() - panel.getScrollPane().getVerticalScrollBar().getVisibleAmount();
        int testValue = Math.max(20, max / 2);
        panel.getScrollPane().getVerticalScrollBar().setValue(testValue);

        panel.saveScrollPosition(largeAlbumList.get(0));
        assertThat(panel.getSavedScrollPosition()).isEqualTo(panel.getScrollPane().getVerticalScrollBar().getValue());
        assertThat(panel.getLastSelectedAlbum()).isEqualTo(largeAlbumList.get(0));

        // Reset scroll value
        panel.getScrollPane().getVerticalScrollBar().setValue(0);
        assertThat(panel.getScrollPane().getVerticalScrollBar().getValue()).isEqualTo(0);

        // Restore
        panel.restoreScrollPosition();
        assertThat(panel.getScrollPane().getVerticalScrollBar().getValue()).isEqualTo(panel.getSavedScrollPosition());
    }

    @Test
    @DisplayName("restoreScrollPosition automatically loads required chunks until lastSelectedAlbum is rendered")
    void testRestoreScrollLoadsNecessaryChunksForSelectedAlbum() {
        java.util.List<Album> largeAlbumList = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Track t = Track.builder().id("t_" + i).filePath("track_" + i + ".mp3").title("Title " + i).album("Album " + i).artist("Artist " + i).build();
            largeAlbumList.add(new Album("Album " + i, "Artist " + i, 2020, 1, 180, t, List.of(t)));
        }

        AlbumGridViewPanel chunkedPanel = new AlbumGridViewPanel(20);
        chunkedPanel.setAlbums(largeAlbumList);
        assertThat(chunkedPanel.getRenderedCardCount()).isEqualTo(20);

        // Select an album in chunk 4 (index 65)
        Album targetAlbum = largeAlbumList.get(65);
        chunkedPanel.setLastSelectedAlbum(targetAlbum);
        chunkedPanel.setSavedScrollPosition(300);

        chunkedPanel.restoreScrollPosition();

        // Needs at least 4 chunks (80 cards) to include index 65
        assertThat(chunkedPanel.getRenderedCardCount()).isGreaterThanOrEqualTo(66);
    }

    @Test
    @DisplayName("setAlbums with preserveScroll=true skips re-rendering when content matches, and resets when preserveScroll=false")
    void testSetAlbumsPreserveScrollBehavior() {
        gridPanel.setSavedScrollPosition(250);
        gridPanel.setLastSelectedAlbum(album2);

        // Identical albums with preserveScroll=true
        gridPanel.setAlbums(gridPanel.getAllAlbums(), true);
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(250);
        assertThat(gridPanel.getLastSelectedAlbum()).isEqualTo(album2);

        // Identical albums with preserveScroll=false (resets)
        gridPanel.setAlbums(gridPanel.getAllAlbums(), false);
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(0);
        assertThat(gridPanel.getLastSelectedAlbum()).isNull();
    }

    @Test
    @DisplayName("scrollToAlbum automatically loads necessary chunks if target album is not yet rendered")
    void testScrollToAlbumLoadsChunks() {
        java.util.List<Album> largeAlbumList = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Track t = Track.builder().id("t_" + i).filePath("track_" + i + ".mp3").title("Title " + i).album("Album " + i).artist("Artist " + i).build();
            largeAlbumList.add(new Album("Album " + i, "Artist " + i, 2020, 1, 180, t, List.of(t)));
        }

        AlbumGridViewPanel chunkedPanel = new AlbumGridViewPanel(10);
        chunkedPanel.setAlbums(largeAlbumList);
        assertThat(chunkedPanel.getRenderedCardCount()).isEqualTo(10);

        Album albumAt45 = largeAlbumList.get(45);
        chunkedPanel.scrollToAlbum(albumAt45);

        // Chunks up to index 45 should have loaded (at least 50 cards)
        assertThat(chunkedPanel.getRenderedCardCount()).isGreaterThanOrEqualTo(46);
    }

    @Test
    @DisplayName("restoreScrollPosition safely no-ops when displayed albums list is empty")
    void testRestoreScrollWithEmptyAlbums() {
        AlbumGridViewPanel emptyPanel = new AlbumGridViewPanel();
        emptyPanel.setAlbums(List.of());
        emptyPanel.setSavedScrollPosition(500);
        emptyPanel.setLastSelectedAlbum(album1);

        // Should return cleanly without throwing any exceptions
        org.assertj.core.api.Assertions.assertThatCode(emptyPanel::restoreScrollPosition)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("restoreScrollPosition safely handles unknown or missing album without throwing")
    void testRestoreScrollWithUnknownAlbum() {
        Track unkTrack = Track.builder().id("unk").filePath("unk.mp3").title("Unk").album("Phantom Album").artist("Phantom Artist").build();
        Album unknownAlbum = new Album("Phantom Album", "Phantom Artist", 2024, 1, 100, unkTrack, List.of(unkTrack));

        gridPanel.setLastSelectedAlbum(unknownAlbum);
        gridPanel.setSavedScrollPosition(0);

        org.assertj.core.api.Assertions.assertThatCode(gridPanel::restoreScrollPosition)
                .doesNotThrowAnyException();
        // Rendered cards remain unchanged
        assertThat(gridPanel.getRenderedCardCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("setSavedScrollPosition clamps negative values to zero")
    void testNegativeSavedScrollPositionClampedToZero() {
        gridPanel.setSavedScrollPosition(-150);
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("setAlbums with preserveScroll=true re-renders cards if album content has actually changed")
    void testSetAlbumsPreserveScrollWithModifiedContentReRendersSafely() {
        Track t3 = Track.builder().id("3").filePath("tracks/3.mp3").title("Song 3").artist("Led Zeppelin").album("IV").build();
        Album album3 = Album.fromTracks(List.of(t3)).get(0);

        gridPanel.setSavedScrollPosition(100);
        gridPanel.setLastSelectedAlbum(album3);

        // Update with modified list containing 3 albums
        gridPanel.setAlbums(List.of(album1, album2, album3), true);

        assertThat(gridPanel.getAllAlbums()).hasSize(3);
        assertThat(gridPanel.getRenderedCardCount()).isEqualTo(3);
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(100);
    }
}
