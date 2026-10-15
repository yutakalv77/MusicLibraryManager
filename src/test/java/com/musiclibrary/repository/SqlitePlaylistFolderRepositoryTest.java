package com.musiclibrary.repository;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.PlaylistFolder;
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

class SqlitePlaylistFolderRepositoryTest {

    private DatabaseManager dbManager;
    private SqlitePlaylistFolderRepository folderRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);
        folderRepository = new SqlitePlaylistFolderRepository(dbManager);
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("save, findById, and deleteById work correctly")
    void testCrudOperations() {
        PlaylistFolder folder = new PlaylistFolder("folder-1", Account.DEFAULT_ACCOUNT_ID, null, "Work BGM", 0, null, null);
        folderRepository.save(folder);

        Optional<PlaylistFolder> found = folderRepository.findById("folder-1");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Work BGM");
        assertThat(found.get().isRootFolder()).isTrue();

        // Update
        folder.setName("Deep Focus");
        folder.setPosition(1);
        folderRepository.save(folder);

        Optional<PlaylistFolder> updated = folderRepository.findById("folder-1");
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Deep Focus");
        assertThat(updated.get().getPosition()).isEqualTo(1);

        // Delete
        folderRepository.deleteById("folder-1");
        assertThat(folderRepository.findById("folder-1")).isEmpty();
    }

    @Test
    @DisplayName("findByAccountId and findByParentFolderId handle hierarchy")
    void testHierarchicalFolders() {
        PlaylistFolder root1 = new PlaylistFolder("r1", Account.DEFAULT_ACCOUNT_ID, null, "Root 1", 0, null, null);
        PlaylistFolder root2 = new PlaylistFolder("r2", Account.DEFAULT_ACCOUNT_ID, null, "Root 2", 1, null, null);
        PlaylistFolder child1 = new PlaylistFolder("c1", Account.DEFAULT_ACCOUNT_ID, "r1", "Child 1", 0, null, null);
        PlaylistFolder child2 = new PlaylistFolder("c2", Account.DEFAULT_ACCOUNT_ID, "r1", "Child 2", 1, null, null);

        folderRepository.saveAll(List.of(root1, root2, child1, child2));

        List<PlaylistFolder> all = folderRepository.findByAccountId(Account.DEFAULT_ACCOUNT_ID);
        assertThat(all).hasSize(4);

        List<PlaylistFolder> roots = folderRepository.findByParentFolderId(Account.DEFAULT_ACCOUNT_ID, null);
        assertThat(roots).extracting(PlaylistFolder::getName).containsExactly("Root 1", "Root 2");

        List<PlaylistFolder> children = folderRepository.findByParentFolderId(Account.DEFAULT_ACCOUNT_ID, "r1");
        assertThat(children).extracting(PlaylistFolder::getName).containsExactly("Child 1", "Child 2");
    }

    @Test
    @DisplayName("Deleting a parent folder cascades to child and descendant folders")
    void testFolderDeletionCascadesToSubfolders() {
        PlaylistFolder root = new PlaylistFolder("p-root", Account.DEFAULT_ACCOUNT_ID, null, "Parent", 0, null, null);
        PlaylistFolder child = new PlaylistFolder("p-child", Account.DEFAULT_ACCOUNT_ID, "p-root", "Child", 0, null, null);
        PlaylistFolder grandchild = new PlaylistFolder("p-grandchild", Account.DEFAULT_ACCOUNT_ID, "p-child", "Grandchild", 0, null, null);

        folderRepository.saveAll(List.of(root, child, grandchild));

        // Confirm all 3 folders exist
        assertThat(folderRepository.findById("p-root")).isPresent();
        assertThat(folderRepository.findById("p-child")).isPresent();
        assertThat(folderRepository.findById("p-grandchild")).isPresent();

        // Delete root folder
        folderRepository.deleteById("p-root");

        // Assert all descendants were cascade deleted
        assertThat(folderRepository.findById("p-root")).isEmpty();
        assertThat(folderRepository.findById("p-child")).isEmpty();
        assertThat(folderRepository.findById("p-grandchild")).isEmpty();
    }

    @Test
    @DisplayName("Saving folder with non-existent account ID fails with RepositoryException due to FK constraint")
    void testForeignKeyConstraintOnInvalidAccount() {
        PlaylistFolder invalid = new PlaylistFolder("f-invalid-acc", "no-such-account", null, "Folder", 0, null, null);
        assertThatThrownBy(() -> folderRepository.save(invalid))
                .isInstanceOf(RepositoryException.class);
    }

    @Test
    @DisplayName("Edge cases and null handling across playlist folder repository")
    void testEdgeCasesAndNullHandling() {
        assertThat(folderRepository.findById(null)).isEmpty();
        assertThat(folderRepository.findById("")).isEmpty();
        assertThat(folderRepository.findById("   ")).isEmpty();
        assertThat(folderRepository.findById("non-existent")).isEmpty();

        assertThat(folderRepository.findByAccountId(null)).isEmpty();
        assertThat(folderRepository.findByAccountId("")).isEmpty();
        assertThat(folderRepository.findByAccountId("   ")).isEmpty();

        assertThat(folderRepository.findByParentFolderId(null, null)).isEmpty();
        assertThat(folderRepository.findByParentFolderId("", null)).isEmpty();
        assertThat(folderRepository.findByParentFolderId("   ", null)).isEmpty();

        // Safe delete calls
        folderRepository.deleteById(null);
        folderRepository.deleteById("");
        folderRepository.deleteById("   ");
        folderRepository.deleteById("non-existent");

        // null save throws NPE
        assertThatThrownBy(() -> folderRepository.save(null))
                .isInstanceOf(NullPointerException.class);
    }
}
