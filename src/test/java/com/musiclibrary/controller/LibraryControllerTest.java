package com.musiclibrary.controller;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.model.TrackMetadataUpdate;
import com.musiclibrary.service.metadata.TagWriter;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.service.storage.LibraryStorage;
import com.musiclibrary.ui.panel.SidebarPanel;
import com.musiclibrary.ui.panel.TrackTablePanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryControllerTest {

    @TempDir
    Path tempDir;

    private Library library;
    private SidebarPanel sidebarPanel;
    private TrackTablePanel trackTablePanel;
    private FastSearchIndex searchIndex;
    private LibraryController controller;
    private TestLibraryStorage testStorage;

    private Track track1;
    private Track track2;
    private Track track3;

    @BeforeEach
    void setUp() {
        com.musiclibrary.theme.ThemeManager.getInstance().applyTheme(com.musiclibrary.theme.AppTheme.DARK);
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        library = new Library();
        sidebarPanel = new SidebarPanel();
        trackTablePanel = new TrackTablePanel();
        searchIndex = new FastSearchIndex();
        testStorage = new TestLibraryStorage();

        track1 = Track.builder()
                .id("t1")
                .filePath("tracks/t1.mp3")
                .title("Bohemian Rhapsody")
                .artist("Queen")
                .album("A Night at the Opera")
                .trackNumber(11)
                .dateAdded(Instant.ofEpochMilli(1000))
                .build();

        track2 = Track.builder()
                .id("t2")
                .filePath("tracks/t2.mp3")
                .title("Another One Bites the Dust")
                .artist("Queen")
                .album("The Game")
                .trackNumber(3)
                .dateAdded(Instant.ofEpochMilli(3000))
                .build();

        track3 = Track.builder()
                .id("t3")
                .filePath("tracks/t3.mp3")
                .title("Stairway to Heaven")
                .artist("Led Zeppelin")
                .album("Led Zeppelin IV")
                .trackNumber(4)
                .dateAdded(Instant.ofEpochMilli(2000))
                .build();

        library.addTrack(track1);
        library.addTrack(track2);
        library.addTrack(track3);
        searchIndex.indexTracks(library.getTracks());

        controller = new LibraryController(
                library,
                sidebarPanel,
                trackTablePanel,
                testStorage,
                Path.of("test-library.json"),
                searchIndex
        );
    }

    @AfterEach
    void tearDown() {
        if (controller != null) {
            controller.dispose();
        }
    }

    @Test
    @DisplayName("createPlaylist creates playlist, updates sidebar, displays it, and triggers storage save")
    void testCreatePlaylist() throws InterruptedException {
        Playlist playlist = controller.createPlaylist("My Rock Mix");

        assertThat(playlist).isNotNull();
        assertThat(playlist.getName()).isEqualTo("My Rock Mix");
        assertThat(library.getPlaylist(playlist.getId())).isEqualTo(playlist);
        assertThat(controller.getCurrentPlaylistId()).isEqualTo(playlist.getId());
        assertThat(controller.isInPlaylistContext()).isTrue();

        assertThat(testStorage.awaitSave(1000)).isTrue();
    }

    @Test
    @DisplayName("createPlaylist uses default name when given null or blank")
    void testCreatePlaylistDefaultName() {
        Playlist pBlank = controller.createPlaylist("   ");
        assertThat(pBlank.getName()).isEqualTo("New Playlist");

        Playlist pNull = controller.createPlaylist(null);
        assertThat(pNull.getName()).isEqualTo("New Playlist");
    }

    @Test
    @DisplayName("renamePlaylist updates name, updates sidebar, and triggers storage save")
    void testRenamePlaylist() throws InterruptedException {
        Playlist playlist = controller.createPlaylist("Old Name");
        testStorage.reset();

        boolean renamed = controller.renamePlaylist(playlist.getId(), "New Cool Name");
        assertThat(renamed).isTrue();
        assertThat(playlist.getName()).isEqualTo("New Cool Name");
        assertThat(testStorage.awaitSave(1000)).isTrue();

        // Renaming non-existing playlist
        assertThat(controller.renamePlaylist("non-existing-id", "Fail")).isFalse();
    }

    @Test
    @DisplayName("deletePlaylist removes playlist and falls back to ALL_SONGS if active")
    void testDeletePlaylistActiveFallback() throws InterruptedException {
        Playlist p = controller.createPlaylist("To Be Deleted");
        assertThat(controller.getCurrentPlaylistId()).isEqualTo(p.getId());
        testStorage.reset();

        boolean deleted = controller.deletePlaylist(p.getId());
        assertThat(deleted).isTrue();
        assertThat(library.getPlaylist(p.getId())).isNull();
        assertThat(controller.getCurrentPlaylistId()).isNull();
        assertThat(controller.getCurrentSection()).isEqualTo(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(controller.isInPlaylistContext()).isFalse();

        assertThat(testStorage.awaitSave(1000)).isTrue();

        // Deleting non-existing playlist returns false
        assertThat(controller.deletePlaylist("non-existing-id")).isFalse();
    }

    @Test
    @DisplayName("addTracksToPlaylist adds existing tracks and updates table view")
    void testAddTracksToPlaylist() throws InterruptedException {
        Playlist p = controller.createPlaylist("Add Tracks Mix");
        testStorage.reset();

        int added = controller.addTracksToPlaylist(p.getId(), List.of("t1", "t3", "invalid-id"));
        assertThat(added).isEqualTo(2);
        assertThat(p.getTrackIds()).containsExactly("t1", "t3");

        // Verify table displays these 2 tracks
        List<Track> displayed = trackTablePanel.getTableModel().getTracks();
        assertThat(displayed).containsExactly(track1, track3);

        assertThat(testStorage.awaitSave(1000)).isTrue();
    }

    @Test
    @DisplayName("removeTrackFromPlaylist removes track and updates table view")
    void testRemoveTrackFromPlaylist() throws InterruptedException {
        Playlist p = controller.createPlaylist("Remove Mix");
        controller.addTracksToPlaylist(p.getId(), List.of("t1", "t2"));
        testStorage.reset();

        boolean removed = controller.removeTrackFromPlaylist(p.getId(), "t1");
        assertThat(removed).isTrue();
        assertThat(p.getTrackIds()).containsExactly("t2");
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactly(track2);

        assertThat(testStorage.awaitSave(1000)).isTrue();
    }

    @Test
    @DisplayName("reorderTracksInPlaylist changes track order and updates view")
    void testReorderTracksInPlaylist() throws InterruptedException {
        Playlist p = controller.createPlaylist("Reorder Mix");
        controller.addTracksToPlaylist(p.getId(), List.of("t1", "t2", "t3"));
        testStorage.reset();

        // Move index 0 ("t1") to index 2
        boolean reordered = controller.reorderTracksInPlaylist(p.getId(), 0, 2);
        assertThat(reordered).isTrue();
        assertThat(p.getTrackIds()).containsExactly("t2", "t3", "t1");
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactly(track2, track3, track1);

        assertThat(testStorage.awaitSave(1000)).isTrue();

        // Out of bounds
        assertThat(controller.reorderTracksInPlaylist(p.getId(), 0, 99)).isFalse();
    }

    @Test
    @DisplayName("showLibrarySection sorts tracks according to selected section")
    void testShowLibrarySectionSorting() {
        // 1. ALL_SONGS
        controller.showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(controller.isInPlaylistContext()).isFalse();
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactlyInAnyOrder(track1, track2, track3);

        // 2. ARTISTS: Led Zeppelin (t3) -> Queen (t1, t2 sorted by album/trackNum: Opera t1, Game t2)
        controller.showLibrarySection(SidebarPanel.LibrarySection.ARTISTS);
        List<Track> artistSorted = trackTablePanel.getTableModel().getTracks();
        assertThat(artistSorted.get(0)).isEqualTo(track3); // Led Zeppelin
        assertThat(artistSorted.get(1)).isEqualTo(track1); // Queen - A Night at the Opera
        assertThat(artistSorted.get(2)).isEqualTo(track2); // Queen - The Game

        // 3. ALBUMS: A Night at the Opera (t1) -> Led Zeppelin IV (t3) -> The Game (t2)
        controller.showLibrarySection(SidebarPanel.LibrarySection.ALBUMS);
        List<Track> albumSorted = trackTablePanel.getTableModel().getTracks();
        assertThat(albumSorted.get(0)).isEqualTo(track1);
        assertThat(albumSorted.get(1)).isEqualTo(track3);
        assertThat(albumSorted.get(2)).isEqualTo(track2);

        // 4. RECENTLY_ADDED: t2 (3000ms) -> t3 (2000ms) -> t1 (1000ms)
        controller.showLibrarySection(SidebarPanel.LibrarySection.RECENTLY_ADDED);
        List<Track> recentSorted = trackTablePanel.getTableModel().getTracks();
        assertThat(recentSorted.get(0)).isEqualTo(track2);
        assertThat(recentSorted.get(1)).isEqualTo(track3);
        assertThat(recentSorted.get(2)).isEqualTo(track1);
    }

    @Test
    @DisplayName("deleteTracks in playlist context removes tracks non-destructively from playlist only")
    void testDeleteTracksInPlaylistContext() throws InterruptedException {
        Playlist p = controller.createPlaylist("Non-destructive Test");
        controller.addTracksToPlaylist(p.getId(), List.of("t1", "t2"));
        testStorage.reset();

        // Delete track1 from current playlist view
        controller.deleteTracks(List.of(track1));

        // Playlist should no longer contain t1
        assertThat(p.getTrackIds()).containsExactly("t2");
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactly(track2);

        // Library domain MUST still contain track1
        assertThat(library.getTrack("t1")).isNotNull();
        assertThat(library.getTracks()).contains(track1);
        assertThat(searchIndex.search("Bohemian")).contains(track1.getId());

        assertThat(testStorage.awaitSave(1000)).isTrue();
    }

    @Test
    @DisplayName("deleteTracks in library section context cascades deletion to library and search index")
    void testDeleteTracksInLibrarySectionContext() throws InterruptedException {
        // Setup playlist containing t1
        Playlist p = controller.createPlaylist("Cascade Test");
        controller.addTracksToPlaylist(p.getId(), List.of("t1", "t2"));

        // Switch to ALL_SONGS section
        controller.showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
        testStorage.reset();

        // Delete track1
        controller.deleteTracks(List.of(track1));

        // Library must NOT contain track1
        assertThat(library.getTrack("t1")).isNull();
        assertThat(library.getTracks()).doesNotContain(track1);

        // Search index must NOT contain track1
        assertThat(searchIndex.search("Bohemian")).isEmpty();

        // Playlist must have cascaded removal
        assertThat(p.getTrackIds()).doesNotContain("t1");

        assertThat(testStorage.awaitSave(1000)).isTrue();
    }

    @Test
    @DisplayName("playPlaylist notifies onPlayTracksAction listener")
    void testPlayPlaylist() {
        Playlist p = controller.createPlaylist("Playback Test");
        controller.addTracksToPlaylist(p.getId(), List.of("t1", "t3"));

        AtomicReference<List<Track>> playedTracks = new AtomicReference<>();
        controller.setOnPlayTracksAction(playedTracks::set);

        controller.playPlaylist(p);

        assertThat(playedTracks.get()).isNotNull();
        assertThat(playedTracks.get()).containsExactly(track1, track3);
    }

    @Test
    @DisplayName("playPlaylist with empty playlist does not trigger listener")
    void testPlayEmptyPlaylist() {
        Playlist empty = controller.createPlaylist("Empty Playlist");

        AtomicBoolean called = new AtomicBoolean(false);
        controller.setOnPlayTracksAction(tracks -> called.set(true));

        controller.playPlaylist(empty);
        assertThat(called.get()).isFalse();
    }

    @Test
    @DisplayName("dispose shuts down executor and marks controller disposed")
    void testDispose() {
        assertThat(controller.isDisposed()).isFalse();
        controller.dispose();
        assertThat(controller.isDisposed()).isTrue();
    }

    @Test
    @DisplayName("createPlaylist allows duplicate names with distinct unique IDs")
    void testCreateDuplicateNamePlaylists() {
        Playlist p1 = controller.createPlaylist("Favorites");
        Playlist p2 = controller.createPlaylist("Favorites");

        assertThat(p1.getId()).isNotEqualTo(p2.getId());
        assertThat(p1.getName()).isEqualTo("Favorites");
        assertThat(p2.getName()).isEqualTo("Favorites");
        assertThat(library.getPlaylists()).contains(p1, p2);
    }

    @Test
    @DisplayName("renamePlaylist with null or blank falls back to localized default name")
    void testRenamePlaylistWithBlankOrNullFallsBackToDefaultName() {
        Playlist p = controller.createPlaylist("Original");

        boolean renamedBlank = controller.renamePlaylist(p.getId(), "   ");
        assertThat(renamedBlank).isTrue();
        assertThat(p.getName()).isEqualTo("New Playlist");

        boolean renamedNull = controller.renamePlaylist(p.getId(), null);
        assertThat(renamedNull).isTrue();
        assertThat(p.getName()).isEqualTo("New Playlist");

        // Non existing playlist
        assertThat(controller.renamePlaylist("non-existing-id", "Valid Name")).isFalse();
    }

    @Test
    @DisplayName("deletePlaylist deleting inactive playlist preserves active playlist selection and table content")
    void testDeleteNonActivePlaylistPreservesActivePlaylistSelection() {
        Playlist active = controller.createPlaylist("Active Playlist");
        controller.addTracksToPlaylist(active.getId(), List.of("t1"));

        Playlist other = controller.createPlaylist("Other Playlist");
        // Flush pending EDT events from createPlaylist
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});

        // Re-select active playlist
        controller.showPlaylist(active.getId());
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> sidebarPanel.selectPlaylist(active.getId()));
        assertThat(controller.getCurrentPlaylistId()).isEqualTo(active.getId());

        // Delete "other" playlist
        boolean deleted = controller.deletePlaylist(other.getId());
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});
        assertThat(deleted).isTrue();
        assertThat(library.getPlaylist(other.getId())).isNull();

        // Active playlist view and selection must remain intact
        assertThat(controller.getCurrentPlaylistId()).isEqualTo(active.getId());
        assertThat(controller.isInPlaylistContext()).isTrue();
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactly(track1);
    }

    @Test
    @DisplayName("addTracksToPlaylist allows duplicate track entries and handles null/empty/invalid IDs")
    void testAddTracksWithDuplicatesAndInvalidIds() {
        Playlist p = controller.createPlaylist("Dup Mix");

        // 1. null list or empty list
        assertThat(controller.addTracksToPlaylist(p.getId(), null)).isEqualTo(0);
        assertThat(controller.addTracksToPlaylist(p.getId(), List.of())).isEqualTo(0);

        // 2. non-existing playlist
        assertThat(controller.addTracksToPlaylist("non-existing", List.of("t1"))).isEqualTo(0);

        // 3. Duplicate additions of same track
        int added1 = controller.addTracksToPlaylist(p.getId(), List.of("t1", "t1"));
        assertThat(added1).isEqualTo(2);
        assertThat(p.getTrackIds()).containsExactly("t1", "t1");

        // 4. Mix of null, blank, invalid, and valid
        List<String> mixed = new ArrayList<>();
        mixed.add(null);
        mixed.add("  ");
        mixed.add("t2");
        mixed.add("unregistered-track-id");
        int added2 = controller.addTracksToPlaylist(p.getId(), mixed);
        assertThat(added2).isEqualTo(1);
        assertThat(p.getTrackIds()).containsExactly("t1", "t1", "t2");
    }

    @Test
    @DisplayName("addTracksToPlaylist to inactive playlist updates playlist without changing currently viewed table")
    void testAddTracksToInactivePlaylistDoesNotAffectCurrentView() {
        Playlist p1 = controller.createPlaylist("Playlist 1");
        controller.addTracksToPlaylist(p1.getId(), List.of("t1"));
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});

        Playlist p2 = controller.createPlaylist("Playlist 2");
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});

        controller.showPlaylist(p1.getId()); // Return to p1 view
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> sidebarPanel.selectPlaylist(p1.getId()));

        // Add track to p2 while viewing p1
        int added = controller.addTracksToPlaylist(p2.getId(), List.of("t2", "t3"));
        assertThat(added).isEqualTo(2);
        assertThat(p2.getTrackIds()).containsExactly("t2", "t3");

        // Currently viewed table MUST still display p1 tracks
        assertThat(controller.getCurrentPlaylistId()).isEqualTo(p1.getId());
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactly(track1);
    }

    @Test
    @DisplayName("reorderTracksInPlaylist safely guards against negative indices and out of bound indices")
    void testReorderTracksInvalidBoundaries() {
        Playlist p = controller.createPlaylist("Bounds Mix");
        controller.addTracksToPlaylist(p.getId(), List.of("t1", "t2", "t3"));

        // 1. Negative fromIndex / toIndex
        assertThat(controller.reorderTracksInPlaylist(p.getId(), -1, 1)).isFalse();
        assertThat(controller.reorderTracksInPlaylist(p.getId(), 0, -1)).isFalse();

        // 2. Beyond size fromIndex / toIndex
        assertThat(controller.reorderTracksInPlaylist(p.getId(), 3, 1)).isFalse();
        assertThat(controller.reorderTracksInPlaylist(p.getId(), 0, 3)).isFalse();

        // 3. Non-existent playlist ID
        assertThat(controller.reorderTracksInPlaylist("non-existent-id", 0, 1)).isFalse();

        // 4. Same index reordering (fromIndex == toIndex) succeeds
        assertThat(controller.reorderTracksInPlaylist(p.getId(), 1, 1)).isTrue();
        assertThat(p.getTrackIds()).containsExactly("t1", "t2", "t3");
    }

    @Test
    @DisplayName("showLibrarySection safely handles null and correctly handles null dateAdded in RECENTLY_ADDED")
    void testShowLibrarySectionBoundaryAndNullSafety() throws Exception {
        // Track with explicitly null dateAdded via reflection
        Track nullDateTrack = Track.builder()
                .id("t-null-date")
                .filePath("tracks/null-date.mp3")
                .title("No Date Track")
                .artist("A Artist")
                .album("A Album")
                .trackNumber(1)
                .build();
        java.lang.reflect.Field dateAddedField = Track.class.getDeclaredField("dateAdded");
        dateAddedField.setAccessible(true);
        dateAddedField.set(nullDateTrack, null);
        assertThat(nullDateTrack.getDateAdded()).isNull();

        library.addTrack(nullDateTrack);

        // 1. null section falls back to ALL_SONGS safely
        controller.showLibrarySection(null);
        assertThat(controller.getCurrentSection()).isEqualTo(SidebarPanel.LibrarySection.ALL_SONGS);
        assertThat(controller.isInPlaylistContext()).isFalse();

        // 2. RECENTLY_ADDED with null dateAdded must put null at the end without NullPointerException
        controller.showLibrarySection(SidebarPanel.LibrarySection.RECENTLY_ADDED);
        List<Track> recentTracks = trackTablePanel.getTableModel().getTracks();
        assertThat(recentTracks).hasSize(4);
        // t2 (3000ms), t3 (2000ms), t1 (1000ms), t-null-date (null at last)
        assertThat(recentTracks.get(0)).isEqualTo(track2);
        assertThat(recentTracks.get(1)).isEqualTo(track3);
        assertThat(recentTracks.get(2)).isEqualTo(track1);
        assertThat(recentTracks.get(3)).isEqualTo(nullDateTrack);
    }

    @Test
    @DisplayName("Context-Aware Deletion completely isolates playlist removal from library and cascades all-songs deletion")
    void testDeleteTracksContextAwarenessMultiPlaylist() {
        // Setup two playlists containing tracks
        Playlist pA = controller.createPlaylist("Playlist Alpha");
        Playlist pB = controller.createPlaylist("Playlist Beta");
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});

        controller.addTracksToPlaylist(pA.getId(), List.of("t1", "t2"));
        controller.addTracksToPlaylist(pB.getId(), List.of("t1", "t3"));

        // 1. Delete t1 while viewing Playlist Alpha
        controller.showPlaylist(pA.getId());
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> sidebarPanel.selectPlaylist(pA.getId()));
        controller.deleteTracks(List.of(track1));
        com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});

        // Playlist Alpha: t1 removed
        assertThat(pA.getTrackIds()).containsExactly("t2");
        assertThat(trackTablePanel.getTableModel().getTracks()).containsExactly(track2);

        // Playlist Beta: t1 MUST still be present!
        assertThat(pB.getTrackIds()).containsExactly("t1", "t3");

        // Library domain & search index: t1 MUST still exist!
        assertThat(library.getTrack("t1")).isNotNull();
        assertThat(searchIndex.search("Bohemian")).contains("t1");

        // 2. Delete t1 while viewing ALL_SONGS section
        controller.showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
        controller.deleteTracks(List.of(track1));

        // Library domain: t1 completely removed
        assertThat(library.getTrack("t1")).isNull();
        assertThat(library.getTracks()).doesNotContain(track1);

        // Search index: t1 completely removed
        assertThat(searchIndex.search("Bohemian")).isEmpty();

        // Cascaded removal in Playlist Beta: t1 must now be gone from pB!
        assertThat(pB.getTrackIds()).containsExactly("t3");
    }

    @Test
    @DisplayName("deleteTracks handles null and empty list safely as a no-op")
    void testDeleteTracksNullAndEmptySafe() {
        // In playlist context
        Playlist p = controller.createPlaylist("Safe Del Mix");
        controller.addTracksToPlaylist(p.getId(), List.of("t1"));
        controller.showPlaylist(p.getId());

        controller.deleteTracks(null);
        assertThat(p.getTrackIds()).containsExactly("t1");

        controller.deleteTracks(List.of());
        assertThat(p.getTrackIds()).containsExactly("t1");

        // In library context
        controller.showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
        controller.deleteTracks(null);
        assertThat(library.getTracks()).hasSize(3);

        controller.deleteTracks(List.of());
        assertThat(library.getTracks()).hasSize(3);
    }

    @Test
    @DisplayName("dispose is idempotent and subsequent saveLibraryAsync calls are safely ignored")
    void testDisposeIdempotencyAndSaveSafeguards() {
        assertThat(controller.isDisposed()).isFalse();

        // Multiple dispose calls
        controller.dispose();
        assertThat(controller.isDisposed()).isTrue();

        controller.dispose();
        assertThat(controller.isDisposed()).isTrue();

        // Calling saveLibraryAsync after dispose does not throw exception
        org.assertj.core.api.Assertions.assertThatCode(() -> controller.saveLibraryAsync())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("saveLibraryAsync safely handles null storage or null path without errors")
    void testSaveLibraryAsyncWithNullStorage() {
        LibraryController noStorageController = new LibraryController(
                library,
                sidebarPanel,
                trackTablePanel,
                null, // null storage
                null, // null path
                searchIndex
        );

        try {
            org.assertj.core.api.Assertions.assertThatCode(noStorageController::saveLibraryAsync)
                    .doesNotThrowAnyException();
        } finally {
            noStorageController.dispose();
        }
    }

    @Test
    @DisplayName("getMissingTracks detects missing tracks and flags them")
    void testGetMissingTracks() {
        List<Track> missing = controller.getMissingTracks();
        assertThat(missing).containsExactlyInAnyOrder(track1, track2, track3);
        assertThat(track1.isMissing()).isTrue();
        assertThat(track2.isMissing()).isTrue();
        assertThat(track3.isMissing()).isTrue();
    }

    @Test
    @DisplayName("relinkTrack updates track path and triggers auto save")
    void testRelinkTrack() throws IOException, InterruptedException {
        Path newFile = Files.createFile(tempDir.resolve("t1_relinked.mp3"));
        testStorage.reset();

        boolean relinked = controller.relinkTrack(track1, newFile);
        assertThat(relinked).isTrue();
        assertThat(track1.isMissing()).isFalse();
        assertThat(track1.getFilePath()).isEqualTo(newFile.toAbsolutePath().normalize().toString());

        boolean saved = testStorage.awaitSave(1000);
        assertThat(saved).isTrue();
    }

    @Test
    @DisplayName("relinkTrack returns false and avoids saving on invalid track or invalid path")
    void testRelinkTrackFailuresAndEdgeCases() throws InterruptedException {
        testStorage.reset();

        // 1. null track
        assertThat(controller.relinkTrack(null, tempDir.resolve("any.mp3"))).isFalse();
        assertThat(testStorage.awaitSave(100)).isFalse();

        // 2. null path
        assertThat(controller.relinkTrack(track1, null)).isFalse();
        assertThat(testStorage.awaitSave(100)).isFalse();

        // 3. non-existent path
        assertThat(controller.relinkTrack(track1, tempDir.resolve("non_existent_file.mp3"))).isFalse();
        assertThat(testStorage.awaitSave(100)).isFalse();

        // 4. directory path instead of regular file
        assertThat(controller.relinkTrack(track1, tempDir)).isFalse();
        assertThat(testStorage.awaitSave(100)).isFalse();
    }

    @Test
    @DisplayName("resolveMissingTracksAsync scans folder, relinks matched tracks and auto-saves")
    void testResolveMissingTracksAsync() throws Exception {
        Path newFile = Files.write(tempDir.resolve("Bohemian Rhapsody.mp3"), new byte[500]);
        testStorage.reset();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger relinkedCounter = new AtomicInteger();

        controller.resolveMissingTracksAsync(tempDir, new com.musiclibrary.service.storage.MissingTrackResolver.ResolveProgressListener() {
            @Override
            public void onProgress(int current, int total, Path currentPath) {}

            @Override
            public void onRelinked(Track track, Path newPath) {}

            @Override
            public void onComplete(int relinkedCount, int remainingCount) {
                relinkedCounter.set(relinkedCount);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(relinkedCounter.get()).isGreaterThanOrEqualTo(1);
        assertThat(track1.isMissing()).isFalse();
        assertThat(track1.getFilePath()).isEqualTo(newFile.toAbsolutePath().normalize().toString());
    }

    @Test
    @DisplayName("updateTrackMetadata updates track model, reindexes search and auto-saves")
    void testUpdateTrackMetadataAsync() throws Exception {
        testStorage.reset();
        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .title("Bohemian Rhapsody (2024 Remaster)")
                .artist("Queen Remastered")
                .year(2024)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        controller.updateTrackMetadata(track1, update, latch::countDown, null);

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(track1.getTitle()).isEqualTo("Bohemian Rhapsody (2024 Remaster)");
        assertThat(track1.getArtist()).isEqualTo("Queen Remastered");
        assertThat(track1.getYear()).isEqualTo(2024);
        assertThat(searchIndex.search("Queen Remastered")).contains(track1.getId());
        assertThat(testStorage.awaitSave(2000)).isTrue();
    }

    @Test
    @DisplayName("updateTracksMetadata batch updates multiple tracks and auto-saves")
    void testUpdateTracksMetadataAsync() throws Exception {
        testStorage.reset();
        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .artist("Queen & Friends")
                .album("Compilation")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        controller.updateTracksMetadata(List.of(track1, track2), update, latch::countDown, null);

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(track1.getArtist()).isEqualTo("Queen & Friends");
        assertThat(track1.getAlbum()).isEqualTo("Compilation");
        assertThat(track2.getArtist()).isEqualTo("Queen & Friends");
        assertThat(track2.getAlbum()).isEqualTo("Compilation");
        // track3 remains unchanged
        assertThat(track3.getArtist()).isEqualTo("Led Zeppelin");
        assertThat(testStorage.awaitSave(2000)).isTrue();
    }

    @Test
    @DisplayName("updateTrackMetadata delegates to TagWriter when file exists")
    void testUpdateTrackMetadataWithTagWriter() throws Exception {
        Path dummyFile = Files.createFile(tempDir.resolve("tag_test.mp3"));
        Track testTrack = Track.builder()
                .id("tag-track")
                .filePath(dummyFile.toString())
                .title("Original Title")
                .build();
        library.addTrack(testTrack);

        AtomicBoolean tagWriterInvoked = new AtomicBoolean(false);
        TagWriter mockWriter = new TagWriter() {
            @Override
            public void write(Path targetFile, TrackMetadataUpdate update) {
                tagWriterInvoked.set(true);
            }

            @Override
            public boolean supports(Path targetFile) {
                return true;
            }
        };

        LibraryController customController = new LibraryController(
                library, sidebarPanel, trackTablePanel, testStorage,
                Path.of("test.json"), searchIndex, null, mockWriter
        );

        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            TrackMetadataUpdate update = TrackMetadataUpdate.builder().title("New Tagged Title").build();
            customController.updateTrackMetadata(testTrack, update, latch::countDown, error -> {
                errorRef.set(error);
                latch.countDown();
            });

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(errorRef.get()).isNull();
            assertThat(tagWriterInvoked.get()).isTrue();
            assertThat(testTrack.getTitle()).isEqualTo("New Tagged Title");
        } finally {
            customController.dispose();
        }
    }

    @Test
    @DisplayName("updateTrackMetadata invokes onError callback when TagWriter fails")
    void testUpdateTrackMetadataErrorCallback() throws Exception {
        Path dummyFile = Files.createFile(tempDir.resolve("failing_test.mp3"));
        Track testTrack = Track.builder()
                .id("fail-track")
                .filePath(dummyFile.toString())
                .title("Original")
                .build();
        library.addTrack(testTrack);

        TagWriter failingWriter = new TagWriter() {
            @Override
            public void write(Path targetFile, TrackMetadataUpdate update) throws IOException {
                throw new IOException("Simulated tag write error");
            }

            @Override
            public boolean supports(Path targetFile) {
                return true;
            }
        };

        LibraryController customController = new LibraryController(
                library, sidebarPanel, trackTablePanel, testStorage,
                Path.of("test.json"), searchIndex, null, failingWriter
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        TrackMetadataUpdate update = TrackMetadataUpdate.builder().title("New Title").build();

        customController.updateTrackMetadata(testTrack, update, () -> {}, error -> {
            errorRef.set(error);
            latch.countDown();
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(errorRef.get()).isInstanceOf(IOException.class).hasMessageContaining("Simulated tag write error");
        customController.dispose();
    }

    @Test
    @DisplayName("updateTracksMetadata triggers onError callback when writing fails during batch operation")
    void testUpdateTracksMetadataBatchErrorCallback() throws Exception {
        Path dummyFile1 = Files.createFile(tempDir.resolve("batch_fail1.mp3"));
        Path dummyFile2 = Files.createFile(tempDir.resolve("batch_fail2.mp3"));

        Track t1 = Track.builder().id("bf1").filePath(dummyFile1.toString()).title("Song 1").build();
        Track t2 = Track.builder().id("bf2").filePath(dummyFile2.toString()).title("Song 2").build();
        library.addTrack(t1);
        library.addTrack(t2);

        com.musiclibrary.service.metadata.TagWriter failingWriter = new com.musiclibrary.service.metadata.TagWriter() {
            @Override
            public void write(Path targetFile, com.musiclibrary.model.TrackMetadataUpdate update) throws IOException {
                if (targetFile.equals(dummyFile2)) {
                    throw new IOException("Batch write error on file 2");
                }
            }

            @Override
            public boolean supports(Path targetFile) {
                return true;
            }
        };

        LibraryController customController = new LibraryController(
                library, sidebarPanel, trackTablePanel, testStorage,
                Path.of("test.json"), searchIndex, null, failingWriter
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        com.musiclibrary.model.TrackMetadataUpdate update = com.musiclibrary.model.TrackMetadataUpdate.builder()
                .artist("Shared Error Artist")
                .build();

        customController.updateTracksMetadata(List.of(t1, t2), update, () -> {}, error -> {
            errorRef.set(error);
            latch.countDown();
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(errorRef.get()).isInstanceOf(IOException.class).hasMessageContaining("Batch write error on file 2");
        customController.dispose();
    }

    @Test
    @DisplayName("updateTrackMetadata immediately refreshes FastSearchIndex and updates table view")
    void testUpdateTrackMetadataReindexesSearchIndexImmediately() throws Exception {
        // Initially, track1 has title "Bohemian Rhapsody", artist "Queen"
        assertThat(searchIndex.search("Bohemian")).contains(track1.getId());
        assertThat(searchIndex.search("NewWave")).isEmpty();

        CountDownLatch latch = new CountDownLatch(1);
        com.musiclibrary.model.TrackMetadataUpdate update = com.musiclibrary.model.TrackMetadataUpdate.builder()
                .title("NewWave Anthem")
                .artist("NewWave Heroes")
                .build();

        controller.updateTrackMetadata(track1, update, latch::countDown, null);

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Search index must now return track1 for new terms, and NOT for old title
        assertThat(searchIndex.search("NewWave")).contains(track1.getId());
        assertThat(searchIndex.search("Heroes")).contains(track1.getId());
        assertThat(searchIndex.search("Bohemian")).doesNotContain(track1.getId());
    }

    @Test
    @DisplayName("updateTracksMetadata safely completes without exception when passed null or empty list or null update")
    void testUpdateTracksMetadataEmptyOrNullSafety() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        com.musiclibrary.model.TrackMetadataUpdate update = com.musiclibrary.model.TrackMetadataUpdate.builder().title("Title").build();

        // 1. null list
        controller.updateTracksMetadata(null, update, latch1::countDown, null);
        assertThat(latch1.await(10, TimeUnit.SECONDS)).isTrue();

        // 2. empty list
        CountDownLatch latch2 = new CountDownLatch(1);
        controller.updateTracksMetadata(List.of(), update, latch2::countDown, null);
        assertThat(latch2.await(10, TimeUnit.SECONDS)).isTrue();

        // 3. null update
        CountDownLatch latch3 = new CountDownLatch(1);
        controller.updateTracksMetadata(List.of(track1), null, latch3::countDown, null);
        assertThat(latch3.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("updateTrackMetadata evicts track from ArtworkCache when artwork is updated or cleared")
    void testUpdateTrackMetadataArtworkEvictsArtworkCache() throws Exception {
        java.awt.image.BufferedImage img1 = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos1 = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img1, "png", baos1);
        byte[] initialArtwork = baos1.toByteArray();

        Track trackWithArt = Track.builder()
                .id("art-evict-track")
                .filePath("tracks/art-evict.mp3")
                .title("Art Track")
                .artworkData(initialArtwork)
                .build();
        library.addTrack(trackWithArt);

        com.musiclibrary.service.cache.ArtworkCache artworkCache = com.musiclibrary.service.cache.ArtworkCache.getInstance();

        // 1. Preload into cache
        artworkCache.getImage(trackWithArt, 50, 50);
        assertThat(artworkCache.contains(trackWithArt.getId(), 50, 50)).isTrue();

        // 2. Update with new artwork
        java.awt.image.BufferedImage img2 = new java.awt.image.BufferedImage(30, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos2 = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img2, "png", baos2);
        byte[] newArtwork = baos2.toByteArray();

        com.musiclibrary.model.TrackMetadataUpdate updateWithNewArt = com.musiclibrary.model.TrackMetadataUpdate.builder()
                .artwork(newArtwork, "image/jpeg")
                .build();

        CountDownLatch latch1 = new CountDownLatch(1);
        controller.updateTrackMetadata(trackWithArt, updateWithNewArt, latch1::countDown, null);
        assertThat(latch1.await(10, TimeUnit.SECONDS)).isTrue();

        // Cached entry for track must be evicted!
        assertThat(artworkCache.contains(trackWithArt.getId(), 50, 50)).isFalse();
        assertThat(trackWithArt.getArtworkData()).containsExactly(newArtwork);

        // 3. Preload again
        artworkCache.getImage(trackWithArt, 50, 50);
        assertThat(artworkCache.contains(trackWithArt.getId(), 50, 50)).isTrue();

        // 4. Update with clear artwork
        com.musiclibrary.model.TrackMetadataUpdate clearArtUpdate = com.musiclibrary.model.TrackMetadataUpdate.builder()
                .clearArtwork()
                .build();

        CountDownLatch latch2 = new CountDownLatch(1);
        controller.updateTrackMetadata(trackWithArt, clearArtUpdate, latch2::countDown, null);
        assertThat(latch2.await(10, TimeUnit.SECONDS)).isTrue();

        // Cached entry must be evicted again!
        assertThat(artworkCache.contains(trackWithArt.getId(), 50, 50)).isFalse();
        assertThat(trackWithArt.getArtworkData()).isNull();
    }

    // --- Test Storage Mock ---

    private static class TestLibraryStorage implements LibraryStorage {
        private final List<Library> savedLibraries = new ArrayList<>();
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void save(Library lib, Path path) throws IOException {
            synchronized (savedLibraries) {
                savedLibraries.add(lib);
            }
            latch.countDown();
        }

        @Override
        public Library load(Path path) throws IOException {
            return new Library();
        }

        public boolean awaitSave(long timeoutMillis) throws InterruptedException {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public void reset() {
            latch = new CountDownLatch(1);
        }
    }
}
