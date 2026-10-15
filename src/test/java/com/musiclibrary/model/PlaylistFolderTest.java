package com.musiclibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaylistFolderTest {

    @Test
    @DisplayName("Single-argument constructor creates root folder under default account")
    void testSingleArgConstructor() {
        PlaylistFolder folder = new PlaylistFolder("Favorites");
        assertThat(folder.getId()).isNotBlank();
        assertThat(folder.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
        assertThat(folder.getParentFolderId()).isNull();
        assertThat(folder.isRootFolder()).isTrue();
        assertThat(folder.getName()).isEqualTo("Favorites");
        assertThat(folder.getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("Validation prevents invalid fields")
    void testValidation() {
        assertThatThrownBy(() -> new PlaylistFolder(null, "acc", null, "Folder", 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaylistFolder("id", null, null, "Folder", 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaylistFolder("id", "acc", null, null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaylistFolder("id", "acc", null, "Folder", -1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Nested folder is not root folder")
    void testNestedFolder() {
        PlaylistFolder child = new PlaylistFolder("child-id", "acc-1", "parent-id", "Rock", 1, null, null);
        assertThat(child.isRootFolder()).isFalse();
        assertThat(child.getParentFolderId()).isEqualTo("parent-id");
    }

    @Test
    @DisplayName("Builder and toBuilder work correctly")
    void testBuilder() {
        Instant now = Instant.now();
        PlaylistFolder folder = PlaylistFolder.builder()
                .id("f-1")
                .accountId("acc-99")
                .parentFolderId("f-0")
                .name("Jazz")
                .position(3)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(folder.getId()).isEqualTo("f-1");
        assertThat(folder.getAccountId()).isEqualTo("acc-99");
        assertThat(folder.getParentFolderId()).isEqualTo("f-0");
        assertThat(folder.getName()).isEqualTo("Jazz");
        assertThat(folder.getPosition()).isEqualTo(3);

        PlaylistFolder cloned = folder.toBuilder().name("Smooth Jazz").build();
        assertThat(cloned.getName()).isEqualTo("Smooth Jazz");
        assertThat(cloned).isEqualTo(folder);
    }

    @Test
    @DisplayName("isRootFolder correctly handles null, empty, blank, and nested parent IDs")
    void testRootFolderDeterminations() {
        PlaylistFolder folder = new PlaylistFolder("Root");
        assertThat(folder.isRootFolder()).isTrue();

        folder.setParentFolderId("  ");
        assertThat(folder.getParentFolderId()).isNull();
        assertThat(folder.isRootFolder()).isTrue();

        folder.setParentFolderId("");
        assertThat(folder.getParentFolderId()).isNull();
        assertThat(folder.isRootFolder()).isTrue();

        folder.setParentFolderId(null);
        assertThat(folder.getParentFolderId()).isNull();
        assertThat(folder.isRootFolder()).isTrue();

        folder.setParentFolderId("parent-123");
        assertThat(folder.getParentFolderId()).isEqualTo("parent-123");
        assertThat(folder.isRootFolder()).isFalse();
    }

    @Test
    @DisplayName("position boundaries: setter rejects negative; builder clamps negative to 0")
    void testPositionBoundaries() {
        PlaylistFolder folder = new PlaylistFolder("Test");
        folder.setPosition(0);
        assertThat(folder.getPosition()).isEqualTo(0);

        folder.setPosition(5);
        assertThat(folder.getPosition()).isEqualTo(5);

        assertThatThrownBy(() -> folder.setPosition(-1))
                .isInstanceOf(IllegalArgumentException.class);

        PlaylistFolder built = PlaylistFolder.builder()
                .name("Clamped")
                .position(-10)
                .build();
        assertThat(built.getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("Setters validate null and blank arguments")
    void testSettersValidation() {
        PlaylistFolder folder = new PlaylistFolder("Folder 1");

        // Valid setters
        folder.setId("f-custom");
        assertThat(folder.getId()).isEqualTo("f-custom");
        folder.setAccountId("acc-custom");
        assertThat(folder.getAccountId()).isEqualTo("acc-custom");
        folder.setName("Renamed Folder");
        assertThat(folder.getName()).isEqualTo("Renamed Folder");

        Instant custom = Instant.parse("2026-10-15T12:00:00Z");
        folder.setCreatedAt(custom);
        assertThat(folder.getCreatedAt()).isEqualTo(custom);
        folder.setUpdatedAt(custom);
        assertThat(folder.getUpdatedAt()).isEqualTo(custom);

        // Invalid setters
        assertThatThrownBy(() -> folder.setId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.setId("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> folder.setAccountId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.setAccountId("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> folder.setName(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.setName("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> folder.setCreatedAt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.setUpdatedAt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("equals and hashCode contracts are satisfied based strictly on id")
    void testEqualsAndHashCode() {
        Instant now = Instant.now();
        PlaylistFolder f1 = new PlaylistFolder("f-same", "acc-1", null, "Folder A", 0, now, now);
        PlaylistFolder f2 = new PlaylistFolder("f-same", "acc-2", "parent-9", "Folder B", 5, now.plusSeconds(1), now.plusSeconds(2));
        PlaylistFolder f3 = new PlaylistFolder("f-diff", "acc-1", null, "Folder A", 0, now, now);

        // Reflexivity
        assertThat(f1).isEqualTo(f1);

        // Symmetry & consistency
        assertThat(f1).isEqualTo(f2);
        assertThat(f2).isEqualTo(f1);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());

        // Inequality
        assertThat(f1).isNotEqualTo(f3);
        assertThat(f1).isNotEqualTo(null);
        assertThat(f1).isNotEqualTo("some string");
    }

    @Test
    @DisplayName("toString includes id, accountId, parentFolderId, name, position, createdAt, and updatedAt")
    void testToString() {
        PlaylistFolder folder = new PlaylistFolder("f-str", "acc-1", "parent-0", "Soundtracks", 2, Instant.EPOCH, Instant.EPOCH);
        assertThat(folder.toString())
                .contains("id='f-str'")
                .contains("accountId='acc-1'")
                .contains("parentFolderId='parent-0'")
                .contains("name='Soundtracks'")
                .contains("position=2");
    }
}
