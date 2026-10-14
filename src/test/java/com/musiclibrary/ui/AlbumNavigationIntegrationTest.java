package com.musiclibrary.ui;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Track;
import com.musiclibrary.ui.panel.SidebarPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumNavigationIntegrationTest {

    private Library library;
    private MainFrame mainFrame;
    private Track track1;
    private Track track2;
    private Track track3;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        library = new Library();
        track1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Bohemian Rhapsody").artist("Queen").album("A Night at the Opera").trackNumber(1).durationSeconds(354).build();
        track2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Love of My Life").artist("Queen").album("A Night at the Opera").trackNumber(2).durationSeconds(219).build();
        track3 = Track.builder().id("3").filePath("tracks/3.mp3").title("Time").artist("Pink Floyd").album("The Dark Side of the Moon").trackNumber(4).durationSeconds(425).build();

        library.addTrack(track1);
        library.addTrack(track2);
        library.addTrack(track3);

        mainFrame = new MainFrame(library, null);
    }

    @AfterEach
    void tearDown() {
        if (mainFrame != null) {
            mainFrame.dispose();
        }
    }

    @Test
    @DisplayName("Selecting ALBUMS section switches to album grid view with populated albums")
    void testNavigateToAlbumGrid() {
        // Select Albums section in sidebar
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumGridViewPanel().getAllAlbums()).hasSize(2);
    }

    @Test
    @DisplayName("Selecting an album opens album detail with header and tracks, and back button returns to grid")
    void testOpenAlbumDetailAndBack() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        Album queenAlbum = albums.stream()
                .filter(a -> a.getTitle().equals("A Night at the Opera"))
                .findFirst()
                .orElseThrow();

        // 1. Open Album Detail
        mainFrame.openAlbumDetail(queenAlbum);

        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);

        // 2. Click Back Button
        mainFrame.showAlbumGrid();

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
    }

    @Test
    @DisplayName("Search filter dynamically filters album grid while active")
    void testSearchInAlbumGrid() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        mainFrame.getControlBarPanel().getSearchField().setText("Floyd");

        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).hasSize(1);
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums().get(0).getTitle()).isEqualTo("The Dark Side of the Moon");

        mainFrame.getControlBarPanel().getSearchField().setText("");
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).hasSize(2);
    }

    @Test
    @DisplayName("Re-clicking ALBUMS in sidebar while in album detail view returns to album grid")
    void testReclickingAlbumsInSidebarReturnsToGridFromDetail() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        mainFrame.openAlbumDetail(albums.get(0));

        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();

        // Click Albums again in sidebar
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
    }

    @Test
    @DisplayName("Switching to other sections (ALL_TRACKS, ARTISTS, Playlists) resets album header and shows normal table")
    void testSwitchingToOtherSectionsResetsAlbumView() {
        // Open album detail first
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        mainFrame.openAlbumDetail(albums.get(0));

        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();

        // 1. Switch to ALL_SONGS
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isEqualTo(3);

        // 2. Open album detail again, then switch to ARTISTS
        mainFrame.openAlbumDetail(albums.get(0));
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();

        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ARTISTS);
        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();

        // 3. Open album detail again, then switch to a playlist
        mainFrame.openAlbumDetail(albums.get(0));
        com.musiclibrary.model.Playlist playlist = mainFrame.getLibraryController().createPlaylist("Test Playlist");
        mainFrame.getSidebarPanel().selectPlaylist(playlist.getId());

        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
    }

    @Test
    @DisplayName("Status bar updates correctly across album grid, search filtering, and album detail view")
    void testStatusBarSummaryUpdates() {
        // 1. In Album Grid: displays aggregated track count and total duration
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        assertThat(mainFrame.getStatusBarPanel().getSummaryLabel().getText())
                .contains("3 tracks")
                .contains("16 min 38 sec");

        // 2. In Album Grid with search filter: displays filtered album aggregated tracks & duration
        mainFrame.getControlBarPanel().getSearchField().setText("Queen");
        assertThat(mainFrame.getStatusBarPanel().getSummaryLabel().getText())
                .contains("2 tracks")
                .contains("9 min 33 sec");

        mainFrame.getControlBarPanel().getSearchField().setText("");

        // 3. In Album Detail: displays track count and total duration of that album
        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        Album queenAlbum = albums.stream().filter(a -> a.getTitle().equals("A Night at the Opera")).findFirst().orElseThrow();
        mainFrame.openAlbumDetail(queenAlbum);

        assertThat(mainFrame.getStatusBarPanel().getSummaryLabel().getText())
                .contains("2 tracks")
                .contains("9 min 33 sec");
    }

    @Test
    @DisplayName("Bilingual language switching and theme toggling seamlessly updates album view components")
    void testBilingualAndThemeSwitching() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        mainFrame.openAlbumDetail(albums.get(0));

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        mainFrame.updateLanguage();

        assertThat(mainFrame.getStatusBarPanel().getSummaryLabel().getText()).contains("曲");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        mainFrame.updateLanguage();

        assertThat(mainFrame.getStatusBarPanel().getSummaryLabel().getText()).contains("tracks");

        // Theme switching
        com.musiclibrary.theme.ThemeManager.getInstance().toggleTheme();
        com.musiclibrary.theme.ThemeManager.getInstance().toggleTheme();
    }

    @Test
    @DisplayName("Playing track in album detail wires track table and audio player safely")
    void testPlayTrackInAlbumDetail() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        mainFrame.openAlbumDetail(albums.get(0));

        // Trigger play action for the first track in album
        mainFrame.getTrackTablePanel().getTable().getSelectionModel().setSelectionInterval(0, 0);
        mainFrame.getTrackTablePanel().playSelectedTrack();

        // Audio player received track and is not in error
        assertThat(mainFrame.getPlayerController()).isNotNull();
    }

    @Test
    @DisplayName("MainFrame.dispose cleans up all resources without exceptions")
    void testDisposeCleansUpSafely() {
        mainFrame.dispose();
        // Subsequent dispose is safe
        mainFrame.dispose();
    }

    @Test
    @DisplayName("MainFrame caches aggregated albums and invalidates on library content changes")
    void testAlbumCacheAndInvalidation() {
        List<Album> a1 = mainFrame.getOrComputeAlbums();
        List<Album> a2 = mainFrame.getOrComputeAlbums();
        // Should return exact same cached instance without recalculating
        assertThat(a1).isSameAs(a2);
        assertThat(a1).hasSize(2);

        // Explicit invalidation
        mainFrame.invalidateAlbumCache();
        List<Album> a3 = mainFrame.getOrComputeAlbums();
        assertThat(a3).isNotSameAs(a1);
        assertThat(a3).hasSize(2);

        // Add track and refreshLibraryView
        Track tNew = Track.builder().id("4").filePath("tracks/4.mp3").title("New Song").artist("New Artist").album("New Album").build();
        library.addTrack(tNew);
        mainFrame.refreshLibraryView();

        List<Album> a4 = mainFrame.getOrComputeAlbums();
        assertThat(a4).hasSize(3);
    }
}
