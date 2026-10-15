package com.musiclibrary.repository;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.PlaylistFolder;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlitePlaylistRepositoryTest {

    private DatabaseManager dbManager;
    private SqliteTrackRepository trackRepository;
    private SqlitePlaylistRepository playlistRepository;
    private SqlitePlaylistFolderRepository folderRepository;

    private Track track1;
    private Track track2;
    private Track track3;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);
        trackRepository = new SqliteTrackRepository(dbManager);
        playlistRepository = new SqlitePlaylistRepository(dbManager);
        folderRepository = new SqlitePlaylistFolderRepository(dbManager);

        track1 = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, Track.builder().filePath("C:/Music/t1.mp3").title("T1").build());
        track2 = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, Track.builder().filePath("C:/Music/t2.mp3").title("T2").build());
        track3 = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, Track.builder().filePath("C:/Music/t3.mp3").title("T3").build());
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("save and findById properly persist and restore playlist metadata and track IDs in exact order")
    void testSaveAndFindById() {
        Playlist playlist = Playlist.builder()
                .id("pl-1")
                .name("Driving Jam")
                .accountId(Account.DEFAULT_ACCOUNT_ID)
                .trackIds(List.of(track2.getId(), track1.getId()))
                .build();

        playlistRepository.save(playlist);

        Optional<Playlist> found = playlistRepository.findById("pl-1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Driving Jam");
        assertThat(found.get().getTrackIds()).containsExactly(track2.getId(), track1.getId());
    }

    @Test
    @DisplayName("findByAccountId fetches all playlists and their track items for the account")
    void testFindByAccountId() {
        Playlist plA = Playlist.builder()
                .id("pl-a")
                .name("Alpha")
                .position(0)
                .trackIds(List.of(track1.getId()))
                .build();
        Playlist plB = Playlist.builder()
                .id("pl-b")
                .name("Beta")
                .position(1)
                .trackIds(List.of(track2.getId(), track3.getId()))
                .build();

        playlistRepository.save(plA);
        playlistRepository.save(plB);

        List<Playlist> list = playlistRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getName()).isEqualTo("Alpha");
        assertThat(list.get(0).getTrackIds()).containsExactly(track1.getId());
        assertThat(list.get(1).getName()).isEqualTo("Beta");
        assertThat(list.get(1).getTrackIds()).containsExactly(track2.getId(), track3.getId());
    }

    @Test
    @DisplayName("Updating playlist tracks preserves new order and elements")
    void testUpdatePlaylistTracks() {
        Playlist playlist = Playlist.builder()
                .id("pl-order")
                .name("Reorder Test")
                .trackIds(List.of(track1.getId(), track2.getId(), track3.getId()))
                .build();
        playlistRepository.save(playlist);

        // Modify order and remove one
        Playlist updated = playlist.toBuilder()
                .trackIds(List.of(track3.getId(), track1.getId()))
                .build();
        playlistRepository.save(updated);

        Optional<Playlist> found = playlistRepository.findById("pl-order");
        assertThat(found).isPresent();
        assertThat(found.get().getTrackIds()).containsExactly(track3.getId(), track1.getId());
    }

    @Test
    @DisplayName("deleteById removes playlist and its playlist_tracks cascade")
    void testDeleteById() {
        Playlist playlist = Playlist.builder()
                .id("pl-del")
                .name("To Delete")
                .trackIds(List.of(track1.getId()))
                .build();
        playlistRepository.save(playlist);

        playlistRepository.deleteById("pl-del");
        assertThat(playlistRepository.findById("pl-del")).isEmpty();
    }

    @Test
    @DisplayName("save rolls back and preserves existing playlist state if error occurs during update")
    void testSaveRollbackPreservesOriginalStateOnError() {
        Playlist original = Playlist.builder()
                .id("pl-rollback-test")
                .name("Original Playlist")
                .trackIds(List.of(track1.getId(), track2.getId()))
                .build();
        playlistRepository.save(original);

        // Attempt to update with a non-existent account ID, which violates foreign key constraint
        Playlist invalidUpdate = original.toBuilder()
                .name("Corrupted Playlist")
                .accountId("non-existent-account-id")
                .trackIds(List.of(track3.getId()))
                .build();

        assertThatThrownBy(() -> playlistRepository.save(invalidUpdate))
                .isInstanceOf(RepositoryException.class);

        // Verify the original playlist was preserved and not left in a partial/deleted state
        Optional<Playlist> recovered = playlistRepository.findById("pl-rollback-test");
        assertThat(recovered).isPresent();
        assertThat(recovered.get().getName()).isEqualTo("Original Playlist");
        assertThat(recovered.get().getTrackIds()).containsExactly(track1.getId(), track2.getId());
    }

    @Test
    @DisplayName("Duplicate track IDs in playlist preserve exact occurrences and positions")
    void testDuplicateTrackIdsPreserveExactOrder() {
        Playlist playlist = Playlist.builder()
                .id("pl-duplicates")
                .name("Repeats Playlist")
                .trackIds(List.of(
                        track1.getId(),
                        track2.getId(),
                        track1.getId(),
                        track3.getId(),
                        track1.getId()
                ))
                .build();

        playlistRepository.save(playlist);

        Optional<Playlist> found = playlistRepository.findById("pl-duplicates");
        assertThat(found).isPresent();
        assertThat(found.get().getTrackIds()).containsExactly(
                track1.getId(),
                track2.getId(),
                track1.getId(),
                track3.getId(),
                track1.getId()
        );
    }

    @Test
    @DisplayName("Deleting a folder sets folder_id to NULL on child playlists via ON DELETE SET NULL")
    void testFolderDeletionSetsPlaylistFolderIdToNull() {
        PlaylistFolder folder = new PlaylistFolder(
                "folder-set-null",
                Account.DEFAULT_ACCOUNT_ID,
                null,
                "Folder To Delete",
                0,
                null,
                null
        );
        folderRepository.save(folder);

        Playlist playlist = Playlist.builder()
                .id("pl-in-folder")
                .name("Playlist In Folder")
                .folderId(folder.getId())
                .trackIds(List.of(track1.getId()))
                .build();
        playlistRepository.save(playlist);

        // Confirm it has the folderId
        Optional<Playlist> initial = playlistRepository.findById("pl-in-folder");
        assertThat(initial).isPresent();
        assertThat(initial.get().getFolderId()).isEqualTo("folder-set-null");

        // Delete folder
        folderRepository.deleteById("folder-set-null");

        // Playlist should still exist, but with folderId set to NULL
        Optional<Playlist> afterDelete = playlistRepository.findById("pl-in-folder");
        assertThat(afterDelete).isPresent();
        assertThat(afterDelete.get().getFolderId()).isNull();
    }

    @Test
    @DisplayName("findByAccountId correctly populates folder_id for root and nested playlists")
    void testPlaylistFolderAssociationInFindByAccountId() {
        PlaylistFolder f1 = new PlaylistFolder("f-a", Account.DEFAULT_ACCOUNT_ID, null, "Folder A", 0, null, null);
        folderRepository.save(f1);

        Playlist plRoot = Playlist.builder().id("p-root").name("Root PL").folderId(null).build();
        Playlist plInFolder = Playlist.builder().id("p-sub").name("Folder PL").folderId("f-a").build();
        playlistRepository.save(plRoot);
        playlistRepository.save(plInFolder);

        List<Playlist> all = playlistRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(all).extracting(Playlist::getId).contains("p-root", "p-sub");

        Playlist foundRoot = all.stream().filter(p -> "p-root".equals(p.getId())).findFirst().orElseThrow();
        assertThat(foundRoot.getFolderId()).isNull();

        Playlist foundInFolder = all.stream().filter(p -> "p-sub".equals(p.getId())).findFirst().orElseThrow();
        assertThat(foundInFolder.getFolderId()).isEqualTo("f-a");
    }

    @Test
    @DisplayName("Edge cases and null handling across playlist repository")
    void testEdgeCasesAndNullHandling() {
        assertThat(playlistRepository.findById(null)).isEmpty();
        assertThat(playlistRepository.findById("")).isEmpty();
        assertThat(playlistRepository.findById("   ")).isEmpty();
        assertThat(playlistRepository.findById("non-existent")).isEmpty();

        assertThat(playlistRepository.findByAccountId(null)).isEmpty();
        assertThat(playlistRepository.findByAccountId("")).isEmpty();
        assertThat(playlistRepository.findByAccountId("   ")).isEmpty();

        // Safe delete calls
        playlistRepository.deleteById(null);
        playlistRepository.deleteById("");
        playlistRepository.deleteById("   ");
        playlistRepository.deleteById("non-existent");

        // null save throws NPE
        assertThatThrownBy(() -> playlistRepository.save(null))
                .isInstanceOf(NullPointerException.class);
    }
}
