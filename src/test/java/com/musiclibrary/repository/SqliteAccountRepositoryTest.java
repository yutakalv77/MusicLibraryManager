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

class SqliteAccountRepositoryTest {

    private DatabaseManager dbManager;
    private SqliteAccountRepository accountRepository;
    private SqliteTrackRepository trackRepository;
    private SqlitePlaylistFolderRepository folderRepository;
    private SqlitePlaylistRepository playlistRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);
        accountRepository = new SqliteAccountRepository(dbManager);
        trackRepository = new SqliteTrackRepository(dbManager);
        folderRepository = new SqlitePlaylistFolderRepository(dbManager);
        playlistRepository = new SqlitePlaylistRepository(dbManager);
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("Default account exists after schema initialization")
    void testDefaultAccountExists() {
        Optional<Account> def = accountRepository.findById(Account.DEFAULT_ACCOUNT_ID);
        assertThat(def).isPresent();
        assertThat(def.get().getName()).isEqualTo(Account.DEFAULT_ACCOUNT_NAME);

        Account orCreate = accountRepository.getOrCreateDefaultAccount();
        assertThat(orCreate.getId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
    }

    @Test
    @DisplayName("save, findById, findByName, and deleteById work correctly")
    void testCrudOperations() {
        Account user1 = new Account("acc-1", "Alice");
        accountRepository.save(user1);

        Optional<Account> found = accountRepository.findById("acc-1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");

        Optional<Account> foundByName = accountRepository.findByName("Alice");
        assertThat(foundByName).isPresent();
        assertThat(foundByName.get().getId()).isEqualTo("acc-1");

        // Update name
        user1.setName("Alice Cooper");
        accountRepository.save(user1);

        Optional<Account> updated = accountRepository.findById("acc-1");
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Alice Cooper");

        // Delete
        accountRepository.deleteById("acc-1");
        assertThat(accountRepository.findById("acc-1")).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all accounts in database")
    void testFindAll() {
        accountRepository.save(new Account("acc-2", "Bob"));
        accountRepository.save(new Account("acc-3", "Charlie"));

        List<Account> accounts = accountRepository.findAll();
        // default + acc-2 + acc-3 = 3 accounts
        assertThat(accounts).hasSize(3);
    }

    @Test
    @DisplayName("Deleting an account cascades to account_tracks, playlist_folders, and playlists, while preserving master tracks")
    void testAccountDeletionCascadesToRelatedTables() {
        String accId = "acc-cascade";
        Account account = new Account(accId, "Cascade User");
        accountRepository.save(account);

        // 1. Link a track to this account
        Track masterTrack = Track.builder().filePath("C:/Music/AccCascade.mp3").title("Shared Audio").build();
        Track importedTrack = trackRepository.importTrack(accId, masterTrack);

        // 2. Create a playlist folder for this account
        PlaylistFolder folder = new PlaylistFolder("f-cascade", accId, null, "Cascade Folder", 0, null, null);
        folderRepository.save(folder);

        // 3. Create a playlist with track for this account
        Playlist playlist = Playlist.builder()
                .id("pl-cascade")
                .name("Cascade Playlist")
                .accountId(accId)
                .folderId("f-cascade")
                .trackIds(List.of(importedTrack.getId()))
                .build();
        playlistRepository.save(playlist);

        // Verify everything exists prior to deletion
        assertThat(accountRepository.findById(accId)).isPresent();
        assertThat(trackRepository.findByAccountId(accId)).hasSize(1);
        assertThat(folderRepository.findByAccountId(accId)).hasSize(1);
        assertThat(playlistRepository.findByAccountId(accId)).hasSize(1);

        // Act: Delete account
        accountRepository.deleteById(accId);

        // Assert: Account is gone
        assertThat(accountRepository.findById(accId)).isEmpty();

        // Account's tracks are gone, BUT the physical master track remains intact
        assertThat(trackRepository.findByAccountId(accId)).isEmpty();
        assertThat(trackRepository.findById(importedTrack.getId())).isPresent();

        // Playlist folders and playlists are cascaded
        assertThat(folderRepository.findByAccountId(accId)).isEmpty();
        assertThat(playlistRepository.findByAccountId(accId)).isEmpty();
    }

    @Test
    @DisplayName("Saving account with duplicate name throws RepositoryException due to UNIQUE constraint")
    void testUniqueNameConstraintViolation() {
        accountRepository.save(new Account("acc-unique-1", "Duplicate Name"));

        Account conflict = new Account("acc-unique-2", "Duplicate Name");
        assertThatThrownBy(() -> accountRepository.save(conflict))
                .isInstanceOf(RepositoryException.class);
    }

    @Test
    @DisplayName("Edge cases and null handling across account repository")
    void testEdgeCasesAndNullHandling() {
        assertThat(accountRepository.findById(null)).isEmpty();
        assertThat(accountRepository.findById("")).isEmpty();
        assertThat(accountRepository.findById("   ")).isEmpty();
        assertThat(accountRepository.findById("non-existent")).isEmpty();

        assertThat(accountRepository.findByName(null)).isEmpty();
        assertThat(accountRepository.findByName("")).isEmpty();
        assertThat(accountRepository.findByName("   ")).isEmpty();
        assertThat(accountRepository.findByName("non-existent")).isEmpty();

        // Safe delete calls
        accountRepository.deleteById(null);
        accountRepository.deleteById("");
        accountRepository.deleteById("   ");
        accountRepository.deleteById("non-existent");

        // null save throws NPE
        assertThatThrownBy(() -> accountRepository.save(null))
                .isInstanceOf(NullPointerException.class);
    }
}
