package com.musiclibrary.service.storage;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.AccountRepository;
import com.musiclibrary.repository.PlaylistRepository;
import com.musiclibrary.repository.SqliteAccountRepository;
import com.musiclibrary.repository.SqlitePlaylistRepository;
import com.musiclibrary.repository.SqliteTrackRepository;
import com.musiclibrary.repository.TrackRepository;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class JsonToSqliteMigratorTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private AccountRepository accountRepository;
    private TrackRepository trackRepository;
    private PlaylistRepository playlistRepository;
    private JsonToSqliteMigrator migrator;
    private JsonLibraryStorage jsonStorage;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);
        accountRepository = new SqliteAccountRepository(dbManager);
        trackRepository = new SqliteTrackRepository(dbManager);
        playlistRepository = new SqlitePlaylistRepository(dbManager);
        migrator = new JsonToSqliteMigrator(dbManager, accountRepository, trackRepository, playlistRepository);
        jsonStorage = new JsonLibraryStorage();
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("isMigrationNeeded returns true only when valid non-empty JSON exists and DB is empty")
    void testIsMigrationNeeded() throws IOException {
        Path jsonPath = tempDir.resolve("library.json");

        // Non-existent
        assertThat(migrator.isMigrationNeeded(jsonPath, Account.DEFAULT_ACCOUNT_ID)).isFalse();

        // Empty file
        Files.createFile(jsonPath);
        assertThat(migrator.isMigrationNeeded(jsonPath, Account.DEFAULT_ACCOUNT_ID)).isFalse();

        // Valid non-empty JSON
        Library library = new Library();
        library.addTrack(Track.builder().filePath("C:/Music/song1.mp3").title("Song 1").build());
        jsonStorage.save(library, jsonPath);
        assertThat(migrator.isMigrationNeeded(jsonPath, Account.DEFAULT_ACCOUNT_ID)).isTrue();

        // If DB already has tracks, migration is NOT needed
        trackRepository.importTrack(Account.DEFAULT_ACCOUNT_ID, Track.builder().filePath("C:/Music/db.mp3").build());
        assertThat(migrator.isMigrationNeeded(jsonPath, Account.DEFAULT_ACCOUNT_ID)).isFalse();
    }

    @Test
    @DisplayName("migrate successfully imports tracks and playlists, backs up JSON, and restores complete library")
    void testSuccessfulMigration() throws IOException {
        Path jsonPath = tempDir.resolve("library.json");

        Track t1 = Track.builder()
                .filePath("C:/Music/Song1.mp3")
                .title("Bohemian Rhapsody")
                .artist("Queen")
                .album("A Night at the Opera")
                .rating(5)
                .playCount(42)
                .build();

        Track t2 = Track.builder()
                .filePath("C:/Music/Song2.mp3")
                .title("Hotel California")
                .artist("Eagles")
                .album("Hotel California")
                .rating(4)
                .playCount(15)
                .build();

        Playlist pl = new Playlist("pl-1", "Rock Classics", List.of(t1.getId(), t2.getId()));

        Library initialLibrary = new Library(List.of(t1, t2), List.of(pl));
        jsonStorage.save(initialLibrary, jsonPath);
        assertThat(Files.exists(jsonPath)).isTrue();

        // Perform migration
        MigrationResult result = migrator.migrate(jsonPath, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSkipped()).isFalse();
        assertThat(result.getTracksMigrated()).isEqualTo(2);
        assertThat(result.getPlaylistsMigrated()).isEqualTo(1);
        assertThat(result.getBackupPath()).isNotNull();
        assertThat(Files.exists(result.getBackupPath())).isTrue();
        assertThat(Files.exists(jsonPath)).isFalse(); // Original file moved to backup

        // Verify SQLite contents
        List<Track> importedTracks = trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(importedTracks).hasSize(2);
        Track queen = importedTracks.stream().filter(t -> t.getTitle().equals("Bohemian Rhapsody")).findFirst().orElseThrow();
        assertThat(queen.getArtist()).isEqualTo("Queen");
        assertThat(queen.getRating()).isEqualTo(5);
        assertThat(queen.getPlayCount()).isEqualTo(42);

        List<Playlist> importedPlaylists = playlistRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(importedPlaylists).hasSize(1);
        Playlist importedPl = importedPlaylists.get(0);
        assertThat(importedPl.getName()).isEqualTo("Rock Classics");
        assertThat(importedPl.getTrackIds()).containsExactly(t1.getId(), t2.getId());
    }

    @Test
    @DisplayName("migrateIfNeeded skips when JSON file does not exist")
    void testMigrateIfNeededSkips() {
        Path nonExistent = tempDir.resolve("none.json");
        MigrationResult result = migrator.migrateIfNeeded(nonExistent, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSkipped()).isTrue();
        assertThat(result.getTracksMigrated()).isEqualTo(0);
    }

    @Test
    @DisplayName("migrate failure returns failure result without deleting source JSON")
    void testMigrationFailure() throws IOException {
        Path corruptJson = tempDir.resolve("corrupt.json");
        Files.writeString(corruptJson, "{ invalid json content !!!");

        MigrationResult result = migrator.migrate(corruptJson, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isNotNull();
        assertThat(Files.exists(corruptJson)).isTrue(); // Original file preserved!
    }

    @Test
    @DisplayName("Duplicate track entries with same filePath in JSON are deduplicated into single master track")
    void testDuplicateTracksInJsonAreDeduplicatedInSqlite() throws IOException {
        Path jsonPath = tempDir.resolve("duplicate-tracks.json");

        Track t1 = Track.builder()
                .filePath("C:/Music/DuplicateSong.mp3")
                .title("Duplicate Title 1")
                .artist("Artist 1")
                .rating(3)
                .playCount(5)
                .build();

        Track t2 = Track.builder()
                .filePath("C:/Music/DuplicateSong.mp3") // Same physical file path
                .title("Duplicate Title 2")
                .artist("Artist 1")
                .rating(5)
                .playCount(10)
                .build();

        Library library = new Library(List.of(t1, t2), List.of());
        jsonStorage.save(library, jsonPath);

        MigrationResult result = migrator.migrate(jsonPath, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTracksMigrated()).isEqualTo(2);

        // Verify deduplication in DB
        List<Track> imported = trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(imported).hasSize(1);
        assertThat(imported.get(0).getFilePath()).isEqualTo("C:/Music/DuplicateSong.mp3");

        // Single master track
        assertThat(trackRepository.findByFilePath("C:/Music/DuplicateSong.mp3")).isPresent();
    }

    @Test
    @DisplayName("Existing library.json.bak is not overwritten when backup file name collision occurs")
    void testExistingBackupFileConflictAvoidsOverwriting() throws IOException {
        Path jsonPath = tempDir.resolve("library.json");
        Path defaultBak = tempDir.resolve("library.json.bak");

        // Pre-create existing backup file
        String originalBakContent = "PREVIOUS_BACKUP_CONTENT_DO_NOT_TOUCH";
        Files.writeString(defaultBak, originalBakContent);

        // Create new library JSON to migrate
        Track track = Track.builder().filePath("C:/Music/Test.mp3").title("Test Song").build();
        jsonStorage.save(new Library(List.of(track), List.of()), jsonPath);

        MigrationResult result = migrator.migrate(jsonPath, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isTrue();
        Path actualBackup = result.getBackupPath();
        assertThat(actualBackup).isNotNull();
        assertThat(actualBackup).isNotEqualTo(defaultBak);
        assertThat(Files.exists(actualBackup)).isTrue();

        // Ensure the original pre-existing backup content was untouched
        assertThat(Files.readString(defaultBak)).isEqualTo(originalBakContent);
    }

    @Test
    @DisplayName("Corrupt and truncated JSON leaves database empty and retains original corrupt file")
    void testCorruptAndMalformedJsonProtection() throws IOException {
        Path truncatedJson = tempDir.resolve("truncated.json");
        Files.writeString(truncatedJson, "{\"tracks\": [{\"title\": \"Incomplete");

        MigrationResult result = migrator.migrate(truncatedJson, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isNotNull();
        assertThat(Files.exists(truncatedJson)).isTrue();

        // Database must remain clean with 0 tracks and 0 playlists
        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();
        assertThat(playlistRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Playlist with duplicate track IDs restores exact ordering and element count in SQLite")
    void testPlaylistDuplicateTrackIdsPreservedInExactOrder() throws IOException {
        Path jsonPath = tempDir.resolve("playlist-duplicates.json");

        Track t1 = Track.builder().filePath("C:/Music/1.mp3").title("Track 1").build();
        Track t2 = Track.builder().filePath("C:/Music/2.mp3").title("Track 2").build();
        Track t3 = Track.builder().filePath("C:/Music/3.mp3").title("Track 3").build();

        List<String> orderWithDuplicates = List.of(
                t1.getId(),
                t2.getId(),
                t1.getId(),
                t3.getId(),
                t2.getId()
        );

        Playlist playlist = new Playlist("pl-repeats", "Repeats Playlist", orderWithDuplicates);
        Library library = new Library(List.of(t1, t2, t3), List.of(playlist));
        jsonStorage.save(library, jsonPath);

        MigrationResult result = migrator.migrate(jsonPath, Account.DEFAULT_ACCOUNT_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPlaylistsMigrated()).isEqualTo(1);

        List<Playlist> loaded = playlistRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getTrackIds()).containsExactly(
                t1.getId(),
                t2.getId(),
                t1.getId(),
                t3.getId(),
                t2.getId()
        );
    }

    @Test
    @DisplayName("Large scale migration with 1,000 tracks and playlists completes quickly within timeout")
    void testLargeScaleMigrationPerformance1000Tracks() throws IOException {
        Path jsonPath = tempDir.resolve("large-library.json");

        int trackCount = 1000;
        List<Track> tracks = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            tracks.add(Track.builder()
                    .filePath("C:/Music/Perf_" + i + ".mp3")
                    .title("Title " + i)
                    .artist("Artist " + (i % 20))
                    .album("Album " + (i % 10))
                    .year(2020)
                    .rating(i % 6)
                    .playCount(i)
                    .build());
        }

        Playlist pl1 = new Playlist("pl-perf-1", "Favorites", List.of(tracks.get(0).getId(), tracks.get(1).getId()));
        Playlist pl2 = new Playlist("pl-perf-2", "All Tracks", tracks.stream().map(Track::getId).toList());
        Library library = new Library(tracks, List.of(pl1, pl2));

        jsonStorage.save(library, jsonPath);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            MigrationResult result = migrator.migrate(jsonPath, Account.DEFAULT_ACCOUNT_ID);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTracksMigrated()).isEqualTo(trackCount);
            assertThat(result.getPlaylistsMigrated()).isEqualTo(2);

            List<Track> dbTracks = trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
            assertThat(dbTracks).hasSize(trackCount);

            List<Playlist> dbPlaylists = playlistRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
            assertThat(dbPlaylists).hasSize(2);
        });
    }

    @Test
    @DisplayName("Edge cases and null safety across migration API")
    void testEdgeCasesAndNullSafety() throws IOException {
        // Null jsonPath
        MigrationResult nullResult = migrator.migrate(null, Account.DEFAULT_ACCOUNT_ID);
        assertThat(nullResult.isSuccess()).isFalse();

        assertThat(migrator.isMigrationNeeded(null, Account.DEFAULT_ACCOUNT_ID)).isFalse();
        assertThat(migrator.isMigrationNeeded(tempDir, Account.DEFAULT_ACCOUNT_ID)).isFalse(); // Directory, not file

        // Fallback to default account when accountId is null or blank
        Path jsonPath = tempDir.resolve("edge.json");
        Track track = Track.builder().filePath("C:/Music/Edge.mp3").title("Edge Song").build();
        jsonStorage.save(new Library(List.of(track), List.of()), jsonPath);

        MigrationResult fallbackResult = migrator.migrate(jsonPath, "   ");
        assertThat(fallbackResult.isSuccess()).isTrue();
        assertThat(trackRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID)).hasSize(1);

        // Custom account auto-creation
        Path jsonCustom = tempDir.resolve("custom.json");
        Track trackCustom = Track.builder().filePath("C:/Music/Custom.mp3").title("Custom Song").build();
        jsonStorage.save(new Library(List.of(trackCustom), List.of()), jsonCustom);

        MigrationResult customResult = migrator.migrate(jsonCustom, "custom-acc");
        assertThat(customResult.isSuccess()).isTrue();
        assertThat(accountRepository.findById("custom-acc")).isPresent();
        assertThat(trackRepository.findByAccountId("custom-acc")).hasSize(1);
    }
}
