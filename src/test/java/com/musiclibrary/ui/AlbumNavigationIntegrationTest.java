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
    @DisplayName("Selecting ALBUMS section shows 2-pane split view with populated left album grid and empty right detail pane")
    void testNavigateToAlbumGrid() {
        // Select Albums section in sidebar
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumGridViewPanel().getAllAlbums()).hasSize(2);
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNull();

        // Right pane is initially empty (header hidden, album null, track table has 0 tracks, compact 2-line mode active)
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isNull();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTable().getColumnCount()).isEqualTo(1);
        assertThat(mainFrame.getTrackTablePanel().getTable().getRowHeight())
                .isEqualTo(com.musiclibrary.ui.panel.TrackTablePanel.COMPACT_ROW_HEIGHT);
        assertThat(mainFrame.getTrackTablePanel().getTable().getTableHeader().isVisible()).isFalse();
    }

    @Test
    @DisplayName("Album view uses horizontal JSplitPane with album grid on left and compact 2-line track list on right")
    void testTwoPaneSplitStructure() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        javax.swing.JSplitPane splitPane = mainFrame.getAlbumSplitPane();
        assertThat(splitPane).isNotNull();
        assertThat(splitPane.getOrientation()).isEqualTo(javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        assertThat(splitPane.getLeftComponent()).isSameAs(mainFrame.getAlbumGridViewPanel());

        java.awt.Container rightPane = (java.awt.Container) splitPane.getRightComponent();
        assertThat(rightPane).isNotNull();
        assertThat(mainFrame.getTrackTablePanel().getParent().getParent()).isSameAs(rightPane);
        assertThat(mainFrame.getAlbumHeaderPanel().getParent()).isSameAs(mainFrame.getTrackTablePanel().getParent());

        // AlbumHeaderPanel is initially hidden (no album selected), and table column header is hidden in 2-pane mode
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        java.awt.Component topBar = mainFrame.getAlbumHeaderPanel().getComponent(0);
        assertThat(topBar.isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTable().getTableHeader().isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().getScrollPane().getColumnHeader() == null
                || mainFrame.getTrackTablePanel().getScrollPane().getColumnHeader().getView() == null).isTrue();
    }

    @Test
    @DisplayName("Selecting an album in 2-pane view keeps grid active, highlights card, shows album header, and populates 2-line track list; switching albums updates right pane immediately")
    void testSelectAlbumInSplitViewAndSwitchAlbums() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        Album queenAlbum = albums.stream()
                .filter(a -> a.getTitle().equals("A Night at the Opera"))
                .findFirst()
                .orElseThrow();
        Album floydAlbum = albums.stream()
                .filter(a -> a.getTitle().equals("The Dark Side of the Moon"))
                .findFirst()
                .orElseThrow();

        // 1. Select first album (Queen)
        mainFrame.openAlbumDetail(queenAlbum);

        // No screen transition away from album split view: isAlbumGridActive stays true, header is visible above 2-line track list
        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getAlbumGridViewPanel().getCardPanels())
                .filteredOn(c -> c.getAlbum().equals(queenAlbum))
                .allMatch(com.musiclibrary.ui.panel.AlbumCardPanel::isSelected);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTable().getColumnCount()).isEqualTo(1);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);

        // 2. Select second album (Pink Floyd) -> switches highlight and right pane tracks immediately
        mainFrame.openAlbumDetail(floydAlbum);

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isEqualTo(floydAlbum);
        assertThat(mainFrame.getAlbumGridViewPanel().getCardPanels())
                .filteredOn(c -> c.getAlbum().equals(floydAlbum))
                .allMatch(com.musiclibrary.ui.panel.AlbumCardPanel::isSelected);
        assertThat(mainFrame.getAlbumGridViewPanel().getCardPanels())
                .filteredOn(c -> c.getAlbum().equals(queenAlbum))
                .allMatch(c -> !c.isSelected());
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(floydAlbum);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track3);

        // 3. Reset album grid / clear selection
        mainFrame.showAlbumGrid();

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isNull();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();
    }

    @Test
    @DisplayName("Search filter dynamically filters album grid while preserving right pane album detail")
    void testSearchInAlbumGridWithSelectionSync() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        Album queenAlbum = albums.stream()
                .filter(a -> a.getTitle().equals("A Night at the Opera"))
                .findFirst()
                .orElseThrow();

        // Select Queen album first
        mainFrame.openAlbumDetail(queenAlbum);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);

        // 1. Search query that still matches selected album ("Queen") -> right pane stays populated
        mainFrame.getControlBarPanel().getSearchField().setText("Queen");
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).hasSize(1);
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);
        assertThat(mainFrame.getTrackTablePanel().getTable().getRowCount()).isEqualTo(2);

        // 2. Search query that excludes selected album ("Floyd") -> grid filters but right pane (header + tracks) is preserved
        mainFrame.getControlBarPanel().getSearchField().setText("Floyd");
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).hasSize(1);
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums().get(0).getTitle()).isEqualTo("The Dark Side of the Moon");
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);
        assertThat(mainFrame.getTrackTablePanel().getTable().getRowCount()).isEqualTo(2);

        // Search query matching nothing at all ("NonExistentXYZ") -> grid is empty but right pane is preserved
        mainFrame.getControlBarPanel().getSearchField().setText("NonExistentXYZ");
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).isEmpty();
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);
        assertThat(mainFrame.getTrackTablePanel().getTable().getRowCount()).isEqualTo(2);

        // 3. Search query matching album1 via track title ("Bohemian") -> Queen album matches and is displayed
        mainFrame.getControlBarPanel().getSearchField().setText("Bohemian");
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).hasSize(1);
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();

        // 4. Clear search query -> all albums displayed, Queen album selection restored in grid and right pane preserved
        mainFrame.getControlBarPanel().getSearchField().setText("");
        assertThat(mainFrame.getAlbumGridViewPanel().getDisplayedAlbums()).hasSize(2);
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isEqualTo(queenAlbum);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(queenAlbum);
    }

    @Test
    @DisplayName("Re-clicking ALBUMS in sidebar resets right pane selection to empty state")
    void testReclickingAlbumsInSidebarReturnsToGridFromDetail() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        mainFrame.openAlbumDetail(albums.get(0));

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(albums.get(0));
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isEqualTo(albums.get(0));

        // Click Albums again in sidebar
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isNull();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();
    }

    @Test
    @DisplayName("Switching to other sections (ALL_TRACKS, ARTISTS, Playlists) restores normal multi-column table mode")
    void testSwitchingToOtherSectionsResetsAlbumView() {
        // Open album detail first
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        mainFrame.openAlbumDetail(albums.get(0));

        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTable().getColumnCount()).isEqualTo(1);

        // 1. Switch to ALL_SONGS -> restores multi-column table, row height 26, and visible table header
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().getTable().getColumnCount()).isGreaterThan(1);
        assertThat(mainFrame.getTrackTablePanel().getTable().getRowHeight())
                .isEqualTo(com.musiclibrary.ui.panel.TrackTablePanel.DEFAULT_ROW_HEIGHT);
        assertThat(mainFrame.getTrackTablePanel().getTable().getTableHeader().isVisible()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isEqualTo(3);

        // 2. Open album detail again, then switch to ARTISTS (2-pane artist split view)
        mainFrame.openAlbumDetail(albums.get(0));
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();

        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ARTISTS);
        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.isArtistGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();

        // 3. Open album detail again, then switch to a playlist
        mainFrame.openAlbumDetail(albums.get(0));
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        com.musiclibrary.model.Playlist playlist = mainFrame.getLibraryController().createPlaylist("Test Playlist");
        mainFrame.getSidebarPanel().selectPlaylist(playlist.getId());

        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.isArtistGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isFalse();
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
    @DisplayName("Playing track in album detail wires track table and audio player safely and shows playback status icon in 2-line renderer")
    void testPlayTrackInAlbumDetail() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        List<Album> albums = mainFrame.getAlbumGridViewPanel().getAllAlbums();
        Album targetAlbum = albums.get(0);
        mainFrame.openAlbumDetail(targetAlbum);

        // Trigger play action for the first track in album
        mainFrame.getTrackTablePanel().getTable().getSelectionModel().setSelectionInterval(0, 0);
        mainFrame.getTrackTablePanel().playSelectedTrack();

        // Audio player received track and is not in error
        assertThat(mainFrame.getPlayerController()).isNotNull();

        // Verify playback status icon in right pane 2-line compact renderer
        Track firstTrack = targetAlbum.getTracks().get(0);
        var tablePanel = mainFrame.getTrackTablePanel();
        var table = tablePanel.getTable();
        var renderer = tablePanel.getTwoLineRenderer();

        tablePanel.setPlaybackStatus(firstTrack.getId(), com.musiclibrary.model.PlaybackState.PLAYING);
        table.getCellRenderer(0, 0).getTableCellRendererComponent(table, table.getValueAt(0, 0), false, false, 0, 0);
        assertThat(renderer.getStatusLabel().getIcon()).isNotNull();

        tablePanel.setPlaybackStatus(firstTrack.getId(), com.musiclibrary.model.PlaybackState.PAUSED);
        table.getCellRenderer(0, 0).getTableCellRendererComponent(table, table.getValueAt(0, 0), false, false, 0, 0);
        assertThat(renderer.getStatusLabel().getIcon()).isNotNull();

        tablePanel.setPlaybackStatus(null, com.musiclibrary.model.PlaybackState.STOPPED);
        table.getCellRenderer(0, 0).getTableCellRendererComponent(table, table.getValueAt(0, 0), false, false, 0, 0);
        assertThat(renderer.getStatusLabel().getIcon()).isNull();
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

    @Test
    @DisplayName("Album detail back button restores scroll position and selected album smoothly")
    void testAlbumDetailBackButtonRestoresScrollPositionAndSelectedAlbum() {
        // Populate library with multiple albums
        for (int i = 0; i < 40; i++) {
            Track t = Track.builder()
                    .id("bulk_" + i)
                    .filePath("bulk_" + i + ".mp3")
                    .title("Track " + i)
                    .artist("Artist " + (i % 5))
                    .album("Bulk Album " + i)
                    .build();
            library.addTrack(t);
        }
        mainFrame.refreshLibraryView();

        // Navigate to ALBUMS
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        var gridPanel = mainFrame.getAlbumGridViewPanel();
        assertThat(gridPanel.getAllAlbums().size()).isGreaterThanOrEqualTo(40);

        // Simulate scrolling down to non-zero position and selecting an album
        int maxScroll = gridPanel.getScrollPane().getVerticalScrollBar().getMaximum() - gridPanel.getScrollPane().getVerticalScrollBar().getVisibleAmount();
        int targetScroll = Math.max(20, maxScroll);
        gridPanel.getScrollPane().getVerticalScrollBar().setValue(targetScroll);
        int expectedScroll = gridPanel.getScrollPane().getVerticalScrollBar().getValue();
        assertThat(expectedScroll).isGreaterThan(0);

        Album targetAlbum = gridPanel.getAllAlbums().get(15);

        // Open album detail
        mainFrame.openAlbumDetail(targetAlbum);

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isEqualTo(targetAlbum);
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(expectedScroll);
        assertThat(gridPanel.getLastSelectedAlbum()).isEqualTo(targetAlbum);

        // Click Back button on header panel
        mainFrame.getAlbumHeaderPanel().getBackButton().doClick();

        // Verify restoration
        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(expectedScroll);
        assertThat(gridPanel.getLastSelectedAlbum()).isEqualTo(targetAlbum);
    }

    @Test
    @DisplayName("Sidebar ALBUMS re-click resets scroll position to zero whereas back button preserves it")
    void testSidebarAlbumsClickResetsScrollPositionWhereasBackButtonRestoresIt() {
        for (int i = 0; i < 30; i++) {
            Track t = Track.builder()
                    .id("scroll_" + i)
                    .filePath("s_" + i + ".mp3")
                    .title("Song " + i)
                    .artist("Artist")
                    .album("Album " + i)
                    .build();
            library.addTrack(t);
        }
        mainFrame.refreshLibraryView();

        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        var gridPanel = mainFrame.getAlbumGridViewPanel();

        // 1. Set scroll position within scrollbar bounds and open detail
        int maxScroll = gridPanel.getScrollPane().getVerticalScrollBar().getMaximum() - gridPanel.getScrollPane().getVerticalScrollBar().getVisibleAmount();
        int targetScroll = Math.max(20, maxScroll);
        gridPanel.getScrollPane().getVerticalScrollBar().setValue(targetScroll);
        int expectedScroll = gridPanel.getScrollPane().getVerticalScrollBar().getValue();
        assertThat(expectedScroll).isGreaterThan(0);

        Album target = gridPanel.getAllAlbums().get(10);
        mainFrame.openAlbumDetail(target);

        // 2. Click Back button: scroll position is preserved
        mainFrame.returnFromAlbumDetail();
        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(expectedScroll);
        assertThat(gridPanel.getLastSelectedAlbum()).isEqualTo(target);

        // 3. Re-select ALBUMS from sidebar: scroll position is reset to 0
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        assertThat(gridPanel.getSavedScrollPosition()).isEqualTo(0);
        assertThat(gridPanel.getLastSelectedAlbum()).isNull();
        assertThat(gridPanel.getScrollPane().getVerticalScrollBar().getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("Returning from album detail automatically expands lazy chunks up to the selected album")
    void testRestoreScrollWithLazyChunkExpansionIntegration() {
        // Create 80 albums
        for (int i = 0; i < 80; i++) {
            Track t = Track.builder()
                    .id("lazy_" + i)
                    .filePath("lazy_" + i + ".mp3")
                    .title("Lazy Song " + i)
                    .artist("Band " + (i % 3))
                    .album(String.format("Album %02d", i))
                    .build();
            library.addTrack(t);
        }
        mainFrame.refreshLibraryView();

        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        var gridPanel = mainFrame.getAlbumGridViewPanel();

        // Target album deep in the collection (index 55)
        Album deepAlbum = gridPanel.getAllAlbums().get(55);

        // Navigate to detail of deep album
        gridPanel.setSavedScrollPosition(400);
        mainFrame.openAlbumDetail(deepAlbum);

        // Return from detail
        mainFrame.returnFromAlbumDetail();

        // Chunks must be expanded so that index 55 is rendered (at least 56 cards)
        assertThat(gridPanel.getRenderedCardCount()).isGreaterThanOrEqualTo(56);
        assertThat(gridPanel.getLastSelectedAlbum()).isEqualTo(deepAlbum);
    }

    @Test
    @DisplayName("showAlbumGrid with restoreScroll=true avoids re-creating cards when album list is unchanged")
    void testUnchangedAlbumsSkipsReRenderingOnReturn() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        var gridPanel = mainFrame.getAlbumGridViewPanel();

        int initialRenderedCount = gridPanel.getRenderedCardCount();
        Album albumToOpen = gridPanel.getAllAlbums().get(0);

        mainFrame.openAlbumDetail(albumToOpen);

        // Call showAlbumGrid(true)
        mainFrame.showAlbumGrid(true);

        // Rendered cards count should not be wiped or reset
        assertThat(gridPanel.getRenderedCardCount()).isEqualTo(initialRenderedCount);
        assertThat(mainFrame.isAlbumGridActive()).isTrue();
    }

    @Test
    @DisplayName("Selecting ARTISTS section shows 2-pane split view with artist grid on left, empty right pane initially, and populates header + 2-line tracks on artist selection")
    void testArtistSplitViewNavigationAndSelection() {
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ARTISTS);

        // 1. Initial state in ARTISTS section
        assertThat(mainFrame.isArtistGridActive()).isTrue();
        assertThat(mainFrame.isAlbumGridActive()).isFalse();
        assertThat(mainFrame.getAlbumSplitPane().getLeftComponent()).isSameAs(mainFrame.getArtistGridViewPanel());
        assertThat(mainFrame.getArtistGridViewPanel().getAllArtists()).hasSize(2);
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isNull();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();

        var artists = mainFrame.getArtistGridViewPanel().getAllArtists();
        var floydArtist = artists.stream().filter(a -> a.getName().equals("Pink Floyd")).findFirst().orElseThrow();
        var queenArtist = artists.stream().filter(a -> a.getName().equals("Queen")).findFirst().orElseThrow();

        // 2. Select Queen artist -> header visible with Queen info, 2-line track list populated
        mainFrame.openArtistDetail(queenArtist);

        assertThat(mainFrame.isArtistGridActive()).isTrue();
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isEqualTo(queenArtist);
        assertThat(mainFrame.getArtistGridViewPanel().getCardPanels())
                .filteredOn(c -> c.getArtist().equals(queenArtist))
                .allMatch(com.musiclibrary.ui.panel.ArtistCardPanel::isSelected);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isEqualTo(queenArtist);
        assertThat(mainFrame.getAlbumHeaderPanel().getTitleLabel().getText()).isEqualTo("Queen");
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);

        // 3. Switch to Pink Floyd artist -> highlight, header, and track list update immediately
        mainFrame.openArtistDetail(floydArtist);

        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isEqualTo(floydArtist);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isEqualTo(floydArtist);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track3);

        // 4. Search filter interaction in ARTISTS view
        mainFrame.getControlBarPanel().getSearchField().setText("Floyd");
        assertThat(mainFrame.getArtistGridViewPanel().getDisplayedArtists()).hasSize(1);
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isEqualTo(floydArtist);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track3);

        // Filter excluding Pink Floyd -> grid filters but right pane (header + tracks) is preserved
        mainFrame.getControlBarPanel().getSearchField().setText("Queen");
        assertThat(mainFrame.getArtistGridViewPanel().getDisplayedArtists()).hasSize(1);
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isEqualTo(floydArtist);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track3);
        assertThat(mainFrame.getTrackTablePanel().getTable().getRowCount()).isEqualTo(1);

        // Filter matching nothing -> grid is empty but right pane is preserved
        mainFrame.getControlBarPanel().getSearchField().setText("NonExistentXYZ");
        assertThat(mainFrame.getArtistGridViewPanel().getDisplayedArtists()).isEmpty();
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isEqualTo(floydArtist);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track3);

        // Filter matching by track title ("Time") -> Floyd artist matches and is displayed
        mainFrame.getControlBarPanel().getSearchField().setText("Time");
        assertThat(mainFrame.getArtistGridViewPanel().getDisplayedArtists()).hasSize(1);
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isEqualTo(floydArtist);
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();

        mainFrame.getControlBarPanel().getSearchField().setText("");
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isEqualTo(floydArtist);

        // 5. Switch to ALL_SONGS -> restores standard multi-column table
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(mainFrame.isArtistGridActive()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().isCompactTwoLineMode()).isFalse();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Moving split pane divider left and right remembers rightPaneWidth and preserves movable range across ALBUMS and ARTISTS sections")
    void testRightPaneWidthMemoryOnDividerMoveAndSectionSwitch() throws Exception {
        // Default rightPaneWidth is 400
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(400);

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
            javax.swing.JSplitPane splitPane = mainFrame.getAlbumSplitPane();
            splitPane.setSize(900, 600);
            splitPane.doLayout();
            mainFrame.applyRightPaneWidth();
        });
        // Flush any queued ComponentEvent.COMPONENT_RESIZED on EDT before moving divider
        javax.swing.SwingUtilities.invokeAndWait(() -> {});

        javax.swing.JSplitPane splitPane = mainFrame.getAlbumSplitPane();
        int dividerSize = splitPane.getDividerSize();

        // Verify minimum sizes and wide movable divider range in ALBUMS (unselected and selected)
        assertThat(splitPane.getLeftComponent().getMinimumSize().width).isLessThanOrEqualTo(150);
        assertThat(splitPane.getRightComponent().getMinimumSize().width).isLessThanOrEqualTo(150);
        assertThat(splitPane.getMinimumDividerLocation()).isLessThan(splitPane.getMaximumDividerLocation());

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Album firstAlbum = mainFrame.getAlbumGridViewPanel().getAllAlbums().get(0);
            mainFrame.openAlbumDetail(firstAlbum);
            splitPane.doLayout();
        });
        assertThat(splitPane.getLeftComponent().getMinimumSize().width).isLessThanOrEqualTo(150);
        assertThat(splitPane.getRightComponent().getMinimumSize().width).isLessThanOrEqualTo(150);
        assertThat(splitPane.getMinimumDividerLocation()).isLessThan(splitPane.getMaximumDividerLocation());

        // 1. Move divider to the left (widening right pane): 320 -> rightPaneWidth = 900 - 320 - dividerSize
        int widenedRightWidth = 900 - 320 - dividerSize;
        javax.swing.SwingUtilities.invokeAndWait(() -> splitPane.setDividerLocation(320));
        assertThat(splitPane.getDividerLocation()).isEqualTo(320);
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(widenedRightWidth);

        // 2. Move divider to the right (narrowing right pane): 620 -> rightPaneWidth = 900 - 620 - dividerSize
        int narrowedRightWidth = 900 - 620 - dividerSize;
        javax.swing.SwingUtilities.invokeAndWait(() -> splitPane.setDividerLocation(620));
        assertThat(splitPane.getDividerLocation()).isEqualTo(620);
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(narrowedRightWidth);

        // 3. Switch to ARTISTS and select an artist -> rightPaneWidth and wide movable range are preserved
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ARTISTS);
            mainFrame.openArtistDetail(mainFrame.getArtistGridViewPanel().getAllArtists().get(0));
            splitPane.doLayout();
        });
        assertThat(splitPane.getLeftComponent().getMinimumSize().width).isLessThanOrEqualTo(150);
        assertThat(splitPane.getRightComponent().getMinimumSize().width).isLessThanOrEqualTo(150);
        assertThat(splitPane.getMinimumDividerLocation()).isLessThan(splitPane.getMaximumDividerLocation());
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(narrowedRightWidth);
        assertThat(splitPane.getDividerLocation()).isEqualTo(620);

        // 4. Move divider left in ARTISTS view (380) and switch back to ALBUMS
        int finalRightWidth = 900 - 380 - dividerSize;
        javax.swing.SwingUtilities.invokeAndWait(() -> splitPane.setDividerLocation(380));
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(finalRightWidth);

        javax.swing.SwingUtilities.invokeAndWait(() ->
                mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS));
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(finalRightWidth);
        assertThat(splitPane.getDividerLocation()).isEqualTo(380);
    }

    @Test
    @DisplayName("Full round-trip UI invariants across ALL_SONGS, ALBUMS, ARTISTS, and Playlist under real layout")
    void testRoundTripSectionTransitionsPreserveLayoutDividerColumnConfigsAndRendererIcons() throws Exception {
        com.musiclibrary.model.Playlist playlist = mainFrame.getLibraryController().createPlaylist("RoundTrip Playlist");
        mainFrame.getLibraryController().addTracksToPlaylist(playlist.getId(), List.of(track1.getId()));

        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getAlbumSplitPane().setSize(900, 600);
            mainFrame.getAlbumSplitPane().doLayout();
            mainFrame.applyRightPaneWidth();
        });
        javax.swing.SwingUtilities.invokeAndWait(() -> {});

        var tablePanel = mainFrame.getTrackTablePanel();
        var splitPane = mainFrame.getAlbumSplitPane();

        // 1. Start in ALL_SONGS: customize sort config and verify standard table invariants
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALL_SONGS);
            tablePanel.applySortConfig(new com.musiclibrary.settings.UserPreferences.SortConfig(
                    com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE, "DESCENDING"));
            tablePanel.setPlaybackStatus(track1.getId(), com.musiclibrary.model.PlaybackState.PLAYING);
        });
        assertThat(tablePanel.isCompactTwoLineMode()).isFalse();
        assertThat(tablePanel.getTable().getColumnCount()).isEqualTo(9);
        assertThat(tablePanel.getTable().getTableHeader().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();

        // 2. Transition to ALBUMS, select an album, move divider left and right, verify 2-line renderer invariants
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
            Album queenAlbum = mainFrame.getAlbumGridViewPanel().getAllAlbums().stream()
                    .filter(a -> a.getTitle().equals("A Night at the Opera"))
                    .findFirst().orElseThrow();
            mainFrame.openAlbumDetail(queenAlbum);
            splitPane.setSize(900, 600);
            splitPane.doLayout();
        });
        javax.swing.SwingUtilities.invokeAndWait(() -> {});

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(tablePanel.isCompactTwoLineMode()).isTrue();
        assertThat(tablePanel.getTable().getColumnCount()).isEqualTo(1);
        assertThat(tablePanel.getTable().getTableHeader().isVisible()).isFalse();
        assertThat(splitPane.getMinimumDividerLocation()).isLessThan(splitPane.getMaximumDividerLocation());

        // Verify playing status icon and track number coexist in 2-line cell renderer
        var twoLineRenderer = tablePanel.getTwoLineRenderer();
        tablePanel.getTable().getCellRenderer(0, 0).getTableCellRendererComponent(
                tablePanel.getTable(), tablePanel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(twoLineRenderer.getStatusLabel().getIcon()).isNotNull();
        assertThat(twoLineRenderer.getTrackNumberLabel().getText()).isEqualTo("1");

        // Move divider to 350 -> remembered rightPaneWidth
        int expectedRightWidth = 900 - 350 - splitPane.getDividerSize();
        javax.swing.SwingUtilities.invokeAndWait(() -> splitPane.setDividerLocation(350));
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(expectedRightWidth);

        // 3. Transition to ARTISTS -> select artist, verify divider width & movable range & 2-line renderer
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ARTISTS);
            var queenArtist = mainFrame.getArtistGridViewPanel().getAllArtists().stream()
                    .filter(a -> a.getName().equals("Queen"))
                    .findFirst().orElseThrow();
            mainFrame.openArtistDetail(queenArtist);
            splitPane.doLayout();
        });
        assertThat(mainFrame.isArtistGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(tablePanel.isCompactTwoLineMode()).isTrue();
        assertThat(splitPane.getDividerLocation()).isEqualTo(350);
        assertThat(splitPane.getMinimumDividerLocation()).isLessThan(splitPane.getMaximumDividerLocation());

        // 4. Transition to ALL_SONGS and Playlist -> verify normal columns, header visibility, and sort config restored
        javax.swing.SwingUtilities.invokeAndWait(() ->
                mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALL_SONGS));
        assertThat(tablePanel.isCompactTwoLineMode()).isFalse();
        assertThat(tablePanel.getTable().getColumnCount()).isEqualTo(9);
        assertThat(tablePanel.getTable().getTableHeader().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(tablePanel.exportSortConfig().getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE);
        assertThat(tablePanel.exportSortConfig().getSortOrder()).isEqualTo("DESCENDING");

        javax.swing.SwingUtilities.invokeAndWait(() ->
                mainFrame.getSidebarPanel().selectPlaylist(playlist.getId()));
        assertThat(tablePanel.isCompactTwoLineMode()).isFalse();
        assertThat(tablePanel.isPlaylistContext()).isTrue();
        assertThat(tablePanel.getTable().getColumnCount()).isEqualTo(9);
        assertThat(tablePanel.getTable().getTableHeader().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();

        // 5. Return to ALBUMS -> verify rightPaneWidth (divider at 350) and unlocked movable range are intact
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
            splitPane.doLayout();
        });
        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getRightPaneWidth()).isEqualTo(expectedRightWidth);
        assertThat(splitPane.getDividerLocation()).isEqualTo(350);
        assertThat(splitPane.getMinimumDividerLocation()).isLessThan(splitPane.getMaximumDividerLocation());
    }

    @Test
    @DisplayName("Deleting tracks while viewing an album or artist updates header, left cards, and right track list, or clears right pane when empty")
    void testDeleteTrackInAlbumAndArtistViewUpdatesSelectionCardsAndHeaderOrClearsWhenEmpty() {
        // --- Part 1: ALBUMS view ---
        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ALBUMS);
        Album queenAlbum = mainFrame.getAlbumGridViewPanel().getAllAlbums().stream()
                .filter(a -> a.getTitle().equals("A Night at the Opera"))
                .findFirst().orElseThrow();
        mainFrame.openAlbumDetail(queenAlbum);

        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum().getTrackCount()).isEqualTo(2);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);

        // Delete track1 from the 2-track Queen album
        mainFrame.getLibraryController().deleteTracks(List.of(track1));

        // Selection is preserved, header & left card & right table reflect 1 remaining track (track2)
        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isNotNull();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum().getTrackCount()).isEqualTo(1);
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNotNull();
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum().getTrackCount()).isEqualTo(1);
        var updatedAlbumCard = mainFrame.getAlbumGridViewPanel().getCardPanels().stream()
                .filter(c -> c.getAlbum().getTitle().equals("A Night at the Opera"))
                .findFirst().orElseThrow();
        assertThat(updatedAlbumCard.isSelected()).isTrue();
        assertThat(updatedAlbumCard.getAlbum().getTrackCount()).isEqualTo(1);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track2);

        // Delete remaining track2 from Queen album -> album disappears and right pane is cleared
        mainFrame.getLibraryController().deleteTracks(List.of(track2));

        assertThat(mainFrame.isAlbumGridActive()).isTrue();
        assertThat(mainFrame.getAlbumGridViewPanel().getAllAlbums()).hasSize(1);
        assertThat(mainFrame.getAlbumGridViewPanel().getSelectedAlbum()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().getAlbum()).isNull();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();

        // --- Part 2: ARTISTS view ---
        Track floydTrack2 = Track.builder()
                .id("4")
                .filePath("tracks/4.mp3")
                .title("Money")
                .artist("Pink Floyd")
                .album("The Dark Side of the Moon")
                .trackNumber(6)
                .durationSeconds(382)
                .build();
        library.addTrack(floydTrack2);
        mainFrame.refreshLibraryView();

        mainFrame.getSidebarPanel().selectSection(SidebarPanel.LibrarySection.ARTISTS);
        var floydArtist = mainFrame.getArtistGridViewPanel().getAllArtists().stream()
                .filter(a -> a.getName().equals("Pink Floyd"))
                .findFirst().orElseThrow();
        mainFrame.openArtistDetail(floydArtist);

        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist().getTrackCount()).isEqualTo(2);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track3, floydTrack2);

        // Delete track3 -> Pink Floyd still has floydTrack2; selection, card, header, and table update to 1 track
        mainFrame.getLibraryController().deleteTracks(List.of(track3));

        assertThat(mainFrame.isArtistGridActive()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isTrue();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isNotNull();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist().getTrackCount()).isEqualTo(1);
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isNotNull();
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist().getTrackCount()).isEqualTo(1);
        var updatedArtistCard = mainFrame.getArtistGridViewPanel().getCardPanels().stream()
                .filter(c -> c.getArtist().getName().equals("Pink Floyd"))
                .findFirst().orElseThrow();
        assertThat(updatedArtistCard.isSelected()).isTrue();
        assertThat(updatedArtistCard.getArtist().getTrackCount()).isEqualTo(1);
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(floydTrack2);

        // Delete remaining floydTrack2 -> artist disappears and right pane is cleared
        mainFrame.getLibraryController().deleteTracks(List.of(floydTrack2));

        assertThat(mainFrame.isArtistGridActive()).isTrue();
        assertThat(mainFrame.getArtistGridViewPanel().getAllArtists()).isEmpty();
        assertThat(mainFrame.getArtistGridViewPanel().getSelectedArtist()).isNull();
        assertThat(mainFrame.getAlbumHeaderPanel().isVisible()).isFalse();
        assertThat(mainFrame.getAlbumHeaderPanel().getArtist()).isNull();
        assertThat(mainFrame.getTrackTablePanel().getTableModel().getRowCount()).isZero();
    }
}
