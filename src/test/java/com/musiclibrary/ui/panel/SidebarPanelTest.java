package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Playlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SidebarPanelTest {

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("SidebarPanel initializes library items, playlist list, and new playlist button")
    void testInitialization() {
        SidebarPanel panel = new SidebarPanel();

        assertThat(panel.getLibraryList()).isNotNull();
        assertThat(panel.getLibraryList().getModel().getSize()).isEqualTo(4);
        assertThat(panel.getPlaylistList()).isNotNull();
        assertThat(panel.getNewPlaylistButton()).isNotNull();
        assertThat(panel.getCurrentPlaylists()).isEmpty();
        assertThat(panel.getSelectedPlaylist()).isNull();
    }

    @Test
    @DisplayName("Library items contain All Songs, Artists, Albums, and Recently Added")
    void testLibraryItems() {
        SidebarPanel panel = new SidebarPanel();

        assertThat(panel.getLibraryList().getModel().getElementAt(0)).contains("All Songs");
        assertThat(panel.getLibraryList().getModel().getElementAt(1)).contains("Artists");
        assertThat(panel.getLibraryList().getModel().getElementAt(2)).contains("Albums");
        assertThat(panel.getLibraryList().getModel().getElementAt(3)).contains("Recently Added");
    }

    @Test
    @DisplayName("setPlaylists handles populated list, empty list, and null safely")
    void testSetPlaylists() {
        SidebarPanel panel = new SidebarPanel();
        Playlist p1 = new Playlist("p1", "Rock Classics");
        Playlist p2 = new Playlist("p2", "Jazz Vibes");

        // 1. Populated list
        panel.setPlaylists(List.of(p1, p2));
        assertThat(panel.getCurrentPlaylists()).containsExactly(p1, p2);
        assertThat(panel.getPlaylistList().getModel().getSize()).isEqualTo(2);
        assertThat(panel.getPlaylistList().getModel().getElementAt(0)).contains("Rock Classics");
        assertThat(panel.getPlaylistList().getModel().getElementAt(1)).contains("Jazz Vibes");

        // 2. Empty list
        panel.setPlaylists(Collections.emptyList());
        assertThat(panel.getCurrentPlaylists()).isEmpty();
        assertThat(panel.getPlaylistList().getModel().getSize()).isEqualTo(0);

        // 3. Null list
        panel.setPlaylists(null);
        assertThat(panel.getCurrentPlaylists()).isEmpty();
        assertThat(panel.getPlaylistList().getModel().getSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Playlist selection and mutual deselection with library list work correctly")
    void testSelectionManagement() {
        SidebarPanel panel = new SidebarPanel();
        Playlist p1 = new Playlist("p1", "Workout");
        Playlist p2 = new Playlist("p2", "Chill");
        panel.setPlaylists(List.of(p1, p2));

        // Select playlist
        panel.getPlaylistList().setSelectedIndex(1);
        assertThat(panel.getSelectedPlaylist()).isEqualTo(p2);
        assertThat(panel.getLibraryList().getSelectedIndex()).isEqualTo(-1);

        // Select library item
        panel.getLibraryList().setSelectedIndex(0);
        assertThat(panel.getPlaylistList().getSelectedIndex()).isEqualTo(-1);
        assertThat(panel.getSelectedPlaylist()).isNull();
    }

    @Test
    @DisplayName("updateLanguage updates section titles, fixed nodes, and button text in Japanese and English")
    void testUpdateLanguage() {
        SidebarPanel panel = new SidebarPanel();

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getNewPlaylistButton().getText()).contains("新規プレイリスト");
        assertThat(panel.getLibraryList().getModel().getElementAt(0)).contains("すべての曲");
        assertThat(panel.getLibraryList().getModel().getElementAt(1)).contains("アーティスト");
        assertThat(panel.getLibraryList().getModel().getElementAt(2)).contains("アルバム");
        assertThat(panel.getLibraryList().getModel().getElementAt(3)).contains("最近追加した項目");
        assertThat(panel.getPlayPlaylistItem().getText()).isEqualTo("再生");
        assertThat(panel.getRenamePlaylistItem().getText()).isEqualTo("名前を変更...");
        assertThat(panel.getDeletePlaylistItem().getText()).isEqualTo("プレイリストを削除");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getNewPlaylistButton().getText()).contains("New Playlist");
        assertThat(panel.getLibraryList().getModel().getElementAt(0)).contains("All Songs");
        assertThat(panel.getPlayPlaylistItem().getText()).isEqualTo("Play");
        assertThat(panel.getRenamePlaylistItem().getText()).isEqualTo("Rename...");
        assertThat(panel.getDeletePlaylistItem().getText()).isEqualTo("Delete Playlist");
    }

    @Test
    @DisplayName("Context menu actions (Play, Rename, Delete) trigger corresponding registered callbacks")
    void testContextMenuActionsTriggerCallbacks() {
        SidebarPanel panel = new SidebarPanel();
        Playlist p = new Playlist("p-1", "Workout Hits");
        panel.setPlaylists(List.of(p));
        panel.selectPlaylist("p-1");

        java.util.concurrent.atomic.AtomicReference<Playlist> played = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Playlist> renamed = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Playlist> deleted = new java.util.concurrent.atomic.AtomicReference<>();

        panel.setOnPlaylistPlayAction(played::set);
        panel.setOnPlaylistRenameAction(renamed::set);
        panel.setOnPlaylistDeleteAction(deleted::set);

        // 1. Play
        panel.getPlayPlaylistItem().doClick();
        assertThat(played.get()).isEqualTo(p);

        // 2. Rename
        panel.getRenamePlaylistItem().doClick();
        assertThat(renamed.get()).isEqualTo(p);

        // 3. Delete
        panel.getDeletePlaylistItem().doClick();
        assertThat(deleted.get()).isEqualTo(p);

        // 4. When no playlist is selected, menu item clicks are safe no-ops
        panel.getPlaylistList().clearSelection();
        played.set(null);
        panel.getPlayPlaylistItem().doClick();
        assertThat(played.get()).isNull();

        // 5. When callbacks are null, clicks do not throw
        panel.setOnPlaylistPlayAction(null);
        panel.setOnPlaylistRenameAction(null);
        panel.setOnPlaylistDeleteAction(null);
        panel.selectPlaylist("p-1");
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            panel.getPlayPlaylistItem().doClick();
            panel.getRenamePlaylistItem().doClick();
            panel.getDeletePlaylistItem().doClick();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("New playlist button fires onNewPlaylistAction runnable and safely handles null callback")
    void testNewPlaylistButtonTriggersCallback() {
        SidebarPanel panel = new SidebarPanel();
        java.util.concurrent.atomic.AtomicBoolean clicked = new java.util.concurrent.atomic.AtomicBoolean(false);

        panel.setOnNewPlaylistAction(() -> clicked.set(true));
        panel.getNewPlaylistButton().doClick();
        assertThat(clicked.get()).isTrue();

        // Safe when callback is null
        panel.setOnNewPlaylistAction(null);
        org.assertj.core.api.Assertions.assertThatCode(() -> panel.getNewPlaylistButton().doClick())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Navigation listeners fire upon section or playlist selection and programmatic selectors work")
    void testNavigationCallbacksAndProgrammaticSelection() {
        SidebarPanel panel = new SidebarPanel();
        Playlist p1 = new Playlist("p-1", "Focus");
        Playlist p2 = new Playlist("p-2", "Chill");
        panel.setPlaylists(List.of(p1, p2));

        java.util.concurrent.atomic.AtomicReference<SidebarPanel.LibrarySection> selectedSection = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Playlist> selectedPlaylist = new java.util.concurrent.atomic.AtomicReference<>();

        panel.setOnSectionSelectedAction(selectedSection::set);
        panel.setOnPlaylistSelectedAction(selectedPlaylist::set);

        // 1. Programmatic selectSection
        panel.selectSection(SidebarPanel.LibrarySection.ALBUMS);
        assertThat(panel.getLibraryList().getSelectedIndex()).isEqualTo(SidebarPanel.LibrarySection.ALBUMS.ordinal());
        assertThat(selectedSection.get()).isEqualTo(SidebarPanel.LibrarySection.ALBUMS);
        assertThat(panel.getPlaylistList().getSelectedIndex()).isEqualTo(-1);

        // 2. Programmatic selectPlaylist
        panel.selectPlaylist("p-2");
        assertThat(panel.getPlaylistList().getSelectedIndex()).isEqualTo(1);
        assertThat(selectedPlaylist.get()).isEqualTo(p2);
        assertThat(panel.getLibraryList().getSelectedIndex()).isEqualTo(-1);

        // 3. selectPlaylist with null or invalid ID clears playlist selection
        panel.selectPlaylist(null);
        assertThat(panel.getPlaylistList().getSelectedIndex()).isEqualTo(-1);

        panel.selectPlaylist("p-2");
        panel.selectPlaylist("non-existent-id");
        assertThat(panel.getPlaylistList().getSelectedIndex()).isEqualTo(1); // doesn't clear if non-existent, maintains current
    }

    @Test
    @DisplayName("Tracks dropped on playlist callback can be set and retrieved")
    void testTracksDroppedCallbackWiring() {
        SidebarPanel panel = new SidebarPanel();
        assertThat(panel.getOnTracksDroppedOnPlaylistAction()).isNull();

        java.util.function.BiConsumer<Playlist, List<String>> callback = (pl, ids) -> {};
        panel.setOnTracksDroppedOnPlaylistAction(callback);
        assertThat(panel.getOnTracksDroppedOnPlaylistAction()).isSameAs(callback);
    }
}

