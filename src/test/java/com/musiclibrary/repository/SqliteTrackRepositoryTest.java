package com.musiclibrary.repository;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SqliteTrackRepositoryTest {

    private DatabaseManager dbManager;
    private SqliteAccountRepository accountRepository;
    private SqliteTrackRepository trackRepository;
    private SqlitePlaylistRepository playlistRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);
        accountRepository = new SqliteAccountRepository(dbManager);
        trackRepository = new SqliteTrackRepository(dbManager);
        playlistRepository = new SqlitePlaylistRepository(dbManager);
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("importTrack creates master track and account_track, returning assigned IDs")
    void testImportTrack() {
        Track track = Track.builder()
                .filePath("C:/Music/SongA.mp3")
                .title("Song A")
                .artist("Artist 1")
                .album("Album X")
                .year(2023)
                .rating(4)
                .playCount(10)
                .build();

        Track imported = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, track);

        assertThat(imported.getId()).isNotBlank();
        assertThat(imported.getAccountTrackId()).isNotBlank();
        assertThat(imported.getTitle()).isEqualTo("Song A");
        assertThat(imported.getRating()).isEqualTo(4);
        assertThat(imported.getPlayCount()).isEqualTo(10);

        // Verify findById and findByFilePath
        Optional<Track> foundMaster = trackRepository.findById(imported.getId());
        assertThat(foundMaster).isPresent();
        assertThat(foundMaster.get().getFilePath()).isEqualTo("C:/Music/SongA.mp3");

        Optional<Track> foundByPath = trackRepository.findByFilePath("C:/Music/SongA.mp3");
        assertThat(foundByPath).isPresent();
        assertThat(foundByPath.get().getId()).isEqualTo(imported.getId());
    }

    @Test
    @DisplayName("Same master file can be linked to multiple accounts independently")
    void testMultiAccountFileSharing() {
        accountRepository.save(new Account("user-2", "Second User"));

        Track track = Track.builder()
                .filePath("C:/Music/Shared.mp3")
                .title("Shared Track")
                .artist("Common Artist")
                .album("Common Album")
                .rating(5)
                .playCount(20)
                .build();

        Track importedDefault = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, track);

        Track trackUser2 = Track.builder()
                .filePath("C:/Music/Shared.mp3")
                .title("Shared Track")
                .rating(1)
                .playCount(2)
                .build();

        Track importedUser2 = trackRepository.importTrack("user-2", trackUser2);

        // Same master ID, but different account_track IDs and stats
        assertThat(importedUser2.getId()).isEqualTo(importedDefault.getId());
        assertThat(importedUser2.getAccountTrackId()).isNotEqualTo(importedDefault.getAccountTrackId());

        List<Track> defTracks = trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(defTracks).hasSize(1);
        assertThat(defTracks.get(0).getRating()).isEqualTo(5);
        assertThat(defTracks.get(0).getPlayCount()).isEqualTo(20);

        List<Track> user2Tracks = trackRepository.findByAccountId("user-2");
        assertThat(user2Tracks).hasSize(1);
        assertThat(user2Tracks.get(0).getRating()).isEqualTo(1);
        assertThat(user2Tracks.get(0).getPlayCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateUserData performs pinpoint update on rating and playCount")
    void testUpdateUserData() {
        Track track = Track.builder()
                .filePath("C:/Music/UpdateMe.mp3")
                .title("Update Test")
                .artist("Artist")
                .album("Album")
                .rating(2)
                .playCount(3)
                .build();

        Track imported = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, track);
        Instant now = Instant.now();

        trackRepository.updateUserData(imported.getAccountTrackId(), 5, 25, now);

        Optional<Track> updated = trackRepository.findByAccountAndTrackId(Account.DEFAULT_ACCOUNT_ID, imported.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getRating()).isEqualTo(5);
        assertThat(updated.get().getPlayCount()).isEqualTo(25);
    }

    @Test
    @DisplayName("deleteFromAccount deletes account link while preserving master track")
    void testDeleteFromAccount() {
        Track track = Track.builder()
                .filePath("C:/Music/KeepMaster.mp3")
                .title("Keep Master")
                .build();

        Track imported = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, track);

        trackRepository.deleteFromAccount(Account.DEFAULT_ACCOUNT_ID, imported.getId());

        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();
        assertThat(trackRepository.findById(imported.getId())).isPresent();
    }

    @Test
    @DisplayName("deleteTrackMaster cascades to delete all account links")
    void testDeleteTrackMaster() {
        Track track = Track.builder()
                .filePath("C:/Music/DeleteMaster.mp3")
                .title("Delete Master")
                .build();

        Track imported = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, track);

        trackRepository.deleteTrackMaster(imported.getId());

        assertThat(trackRepository.findById(imported.getId())).isEmpty();
        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("importTracksBatch imports list of tracks inside single transaction")
    void testImportTracksBatch() {
        Track t1 = Track.builder().filePath("C:/Music/1.mp3").title("One").artist("A").album("Al").trackNumber(1).build();
        Track t2 = Track.builder().filePath("C:/Music/2.mp3").title("Two").artist("A").album("Al").trackNumber(2).build();

        trackRepository.importTracksBatch(Account.DEFAULT_ACCOUNT_ID, List.of(t1, t2));

        List<Track> list = trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(list).hasSize(2);
        assertThat(list).extracting(Track::getTitle).containsExactly("One", "Two");
    }

    @Test
    @DisplayName("importTracksBatch rolls back entire transaction if an error occurs mid-batch")
    void testImportTracksBatchRollbackOnError() {
        Track t1 = Track.builder().filePath("C:/Music/Rollback1.mp3").title("Valid One").build();
        Track t2 = Track.builder().filePath("C:/Music/Rollback2.mp3").title("Valid Two").build();

        // 3rd track intentionally causes SQL error (file_path is NULL in DB schema)
        Track invalidTrack = new Track() {
            @Override
            public String getFilePath() {
                return null; // Violates tracks(file_path NOT NULL)
            }
        };

        assertThatThrownBy(() -> trackRepository.importTracksBatch(
                Account.DEFAULT_ACCOUNT_ID,
                List.of(t1, t2, invalidTrack)
        )).isInstanceOf(RepositoryException.class);

        // Verify that neither t1 nor t2 was committed
        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();
        assertThat(trackRepository.findByFilePath("C:/Music/Rollback1.mp3")).isEmpty();
        assertThat(trackRepository.findByFilePath("C:/Music/Rollback2.mp3")).isEmpty();
    }

    @Test
    @DisplayName("Foreign key constraint fails when importing track with non-existent account")
    void testForeignKeyConstraintOnInvalidAccount() {
        Track track = Track.builder().filePath("C:/Music/FkTest.mp3").title("FK Test").build();

        assertThatThrownBy(() -> trackRepository.importTrack("non-existent-acc-id", track))
                .isInstanceOf(RepositoryException.class);

        // Account track shouldn't exist
        assertThat(trackRepository.findByAccountAndTrackId("non-existent-acc-id", track.getId())).isEmpty();
    }

    @Test
    @DisplayName("Deleting track master cascades to account_tracks and playlist_tracks")
    void testTrackMasterCascadeDeletesAccountTracksAndPlaylistTracks() {
        Track track = Track.builder().filePath("C:/Music/CascadeTest.mp3").title("Cascade Song").build();
        Track imported = trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, track);

        Playlist playlist = Playlist.builder()
                .id("pl-cascade-track")
                .name("Playlist With Track")
                .trackIds(List.of(imported.getId()))
                .build();
        playlistRepository.save(playlist);

        // Verify playlist initially contains the track
        Optional<Playlist> savedPl = playlistRepository.findById("pl-cascade-track");
        assertThat(savedPl).isPresent();
        assertThat(savedPl.get().getTrackIds()).containsExactly(imported.getId());

        // Delete track master
        trackRepository.deleteTrackMaster(imported.getId());

        // Master and account_track should be deleted
        assertThat(trackRepository.findById(imported.getId())).isEmpty();
        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();

        // Playlist tracks should have cascaded out
        Optional<Playlist> afterPl = playlistRepository.findById("pl-cascade-track");
        assertThat(afterPl).isPresent();
        assertThat(afterPl.get().getTrackIds()).isEmpty();
    }

    @Test
    @DisplayName("Edge cases and null/blank safety across all track query methods")
    void testFindByIdAndFindByFilePathEdgeCases() {
        assertThat(trackRepository.findById(null)).isEmpty();
        assertThat(trackRepository.findById("")).isEmpty();
        assertThat(trackRepository.findById("   ")).isEmpty();
        assertThat(trackRepository.findById("does-not-exist")).isEmpty();

        assertThat(trackRepository.findByFilePath(null)).isEmpty();
        assertThat(trackRepository.findByFilePath("")).isEmpty();
        assertThat(trackRepository.findByFilePath("   ")).isEmpty();
        assertThat(trackRepository.findByFilePath("C:/NonExistent/file.mp3")).isEmpty();

        assertThat(trackRepository.findByAccountId(null)).isEmpty();
        assertThat(trackRepository.findByAccountId("")).isEmpty();
        assertThat(trackRepository.findByAccountId("   ")).isEmpty();

        assertThat(trackRepository.findByAccountAndTrackId(null, "id")).isEmpty();
        assertThat(trackRepository.findByAccountAndTrackId("acc", null)).isEmpty();
        assertThat(trackRepository.findByAccountAndTrackId("  ", "  ")).isEmpty();

        // Safe deletes with null/blank
        trackRepository.deleteFromAccount(null, "id");
        trackRepository.deleteFromAccount("acc", null);
        trackRepository.deleteTrackMaster(null);
        trackRepository.deleteTrackMaster("   ");
        trackRepository.importTracksBatch(null, List.of());
        trackRepository.importTracksBatch(Account.DEFAULT_ACCOUNT_ID, null);
    }

    @Test
    @DisplayName("updateUserData with non-existent account track ID safely affects zero rows")
    void testUpdateUserDataNonExistentId() {
        // Updating a non-existent account_track_id should not fail or affect anything
        trackRepository.updateUserData("non-existent-at-id", 4, 10, Instant.now());
        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Batch import and query performance with 1,000 tracks completes within timeout")
    void testBatchImportAndQueryPerformance1000Tracks() {
        int count = 1000;
        List<Track> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            batch.add(Track.builder()
                    .filePath("C:/Music/Track_" + i + ".mp3")
                    .title("Title " + i)
                    .artist("Artist " + (i % 50))
                    .album("Album " + (i % 20))
                    .genre("Genre " + (i % 5))
                    .year(2000 + (i % 25))
                    .rating(i % 6)
                    .playCount(i)
                    .build());
        }

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            trackRepository.importTracksBatch(Account.DEFAULT_ACCOUNT_ID, batch);

            List<Track> loaded = trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
            assertThat(loaded).hasSize(count);
        });
    }
}
