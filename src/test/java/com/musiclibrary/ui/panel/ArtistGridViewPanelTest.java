package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Artist;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistGridViewPanelTest {

    private ArtistGridViewPanel gridPanel;
    private Artist artist1;
    private Artist artist2;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        Track t1 = Track.builder().id("1").filePath("1.mp3").title("Song 1").artist("Pink Floyd").album("The Wall").build();
        Track t2 = Track.builder().id("2").filePath("2.mp3").title("Song 2").artist("Queen").album("A Night at the Opera").build();

        List<Artist> artists = Artist.fromTracks(List.of(t1, t2));
        artist1 = artists.get(0); // Pink Floyd
        artist2 = artists.get(1); // Queen

        gridPanel = new ArtistGridViewPanel();
        gridPanel.setArtists(artists);
    }

    @Test
    @DisplayName("setArtists initializes all and displayed artists and handles null safely")
    void testSetArtists() {
        assertThat(gridPanel.getAllArtists()).hasSize(2);
        assertThat(gridPanel.getDisplayedArtists()).hasSize(2);
        assertThat(gridPanel.getRenderedCardCount()).isEqualTo(2);

        gridPanel.setArtists(null);
        assertThat(gridPanel.getAllArtists()).isEmpty();
        assertThat(gridPanel.getDisplayedArtists()).isEmpty();
    }

    @Test
    @DisplayName("filterArtists filters by artist name case-insensitively and retains or clears selection")
    void testFilterArtistsAndSelectionSync() {
        AtomicReference<Artist> callbackArtist = new AtomicReference<>(artist2);
        gridPanel.setOnArtistSelectedAction(callbackArtist::set);

        gridPanel.setSelectedArtist(artist2);
        assertThat(gridPanel.getSelectedArtist()).isEqualTo(artist2);

        // Filter matching artist2 ("Queen")
        gridPanel.filterArtists("  queen  ");
        assertThat(gridPanel.getDisplayedArtists()).hasSize(1);
        assertThat(gridPanel.getDisplayedArtists().get(0)).isEqualTo(artist2);
        assertThat(gridPanel.getSelectedArtist()).isEqualTo(artist2);
        assertThat(gridPanel.getCardPanels().get(0).isSelected()).isTrue();
        assertThat(callbackArtist.get()).isEqualTo(artist2);

        // Filter excluding artist2 ("Pink")
        gridPanel.filterArtists("PINK");
        assertThat(gridPanel.getDisplayedArtists()).hasSize(1);
        assertThat(gridPanel.getDisplayedArtists().get(0)).isEqualTo(artist1);
        assertThat(gridPanel.getSelectedArtist()).isNull();
        assertThat(gridPanel.getCardPanels()).allMatch(c -> !c.isSelected());
        // onArtistSelectedAction is not triggered with null so right pane is not cleared
        assertThat(callbackArtist.get()).isEqualTo(artist2);

        // Filter matching artist2 via track title ("Song 2")
        gridPanel.filterArtists("Song 2");
        assertThat(gridPanel.getDisplayedArtists()).hasSize(1);
        assertThat(gridPanel.getDisplayedArtists().get(0)).isEqualTo(artist2);
        assertThat(gridPanel.getSelectedArtist()).isEqualTo(artist2);
        assertThat(gridPanel.getCardPanels().get(0).isSelected()).isTrue();

        // Filter matching artist1 via track album ("The Wall")
        gridPanel.filterArtists("The Wall");
        assertThat(gridPanel.getDisplayedArtists()).hasSize(1);
        assertThat(gridPanel.getDisplayedArtists().get(0)).isEqualTo(artist1);
        assertThat(gridPanel.getSelectedArtist()).isNull();
        assertThat(gridPanel.getCardPanels()).allMatch(c -> !c.isSelected());
        assertThat(callbackArtist.get()).isEqualTo(artist2);

        // Filter that matches nothing at all ("NonExistentXYZ")
        gridPanel.filterArtists("NonExistentXYZ");
        assertThat(gridPanel.getDisplayedArtists()).isEmpty();
        assertThat(gridPanel.getSelectedArtist()).isNull();
        assertThat(gridPanel.getCardPanels()).isEmpty();
        assertThat(callbackArtist.get()).isEqualTo(artist2);

        // Clear filter
        gridPanel.filterArtists("");
        assertThat(gridPanel.getDisplayedArtists()).hasSize(2);
        assertThat(gridPanel.getSelectedArtist()).isEqualTo(artist2);
        assertThat(gridPanel.getCardPanels().get(1).isSelected()).isTrue();
        assertThat(callbackArtist.get()).isEqualTo(artist2);
    }

    @Test
    @DisplayName("Clicking an artist card updates selectedArtist, highlights card, and triggers callback")
    void testCardClickSelection() {
        AtomicReference<Artist> selectedRef = new AtomicReference<>();
        gridPanel.setOnArtistSelectedAction(selectedRef::set);

        ArtistCardPanel card = gridPanel.getCardPanels().get(0);
        for (var ml : card.getMouseListeners()) {
            ml.mouseClicked(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false));
        }

        assertThat(selectedRef.get()).isEqualTo(artist1);
        assertThat(gridPanel.getSelectedArtist()).isEqualTo(artist1);
        assertThat(card.isSelected()).isTrue();
    }

    @Test
    @DisplayName("Lazy chunk rendering and scroll restoration expand chunks as needed")
    void testLazyChunkRenderingAndScrollRestoration() {
        List<Artist> largeList = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            Track t = Track.builder().id("t_" + i).filePath(i + ".mp3").title("Song " + i).artist(String.format("Artist %02d", i)).album("Album").build();
            largeList.add(new Artist(String.format("Artist %02d", i), 1, 1, 180, t, List.of(t)));
        }

        ArtistGridViewPanel chunkedPanel = new ArtistGridViewPanel(20);
        chunkedPanel.setArtists(largeList);
        assertThat(chunkedPanel.getRenderedCardCount()).isEqualTo(20);

        chunkedPanel.loadNextChunk();
        assertThat(chunkedPanel.getRenderedCardCount()).isEqualTo(40);

        Artist deepArtist = largeList.get(52);
        chunkedPanel.scrollToArtist(deepArtist);
        assertThat(chunkedPanel.getRenderedCardCount()).isGreaterThanOrEqualTo(53);

        chunkedPanel.setSavedScrollPosition(120);
        chunkedPanel.setLastSelectedArtist(deepArtist);
        chunkedPanel.restoreScrollPosition();
        assertThat(chunkedPanel.getSavedScrollPosition()).isEqualTo(120);
    }

    @Test
    @DisplayName("updateLanguage updates empty state label and card texts in English and Japanese")
    void testUpdateLanguage() {
        gridPanel.filterArtists("Nonexistent");

        assertThat(gridPanel.getEmptyLabel().getText()).isEqualTo("No artists found");

        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        gridPanel.updateLanguage();
        assertThat(gridPanel.getEmptyLabel().getText()).isEqualTo("アーティストがありません");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        gridPanel.updateLanguage();
        assertThat(gridPanel.getEmptyLabel().getText()).isEqualTo("No artists found");
    }
}
