package com.musiclibrary.ui;

import com.musiclibrary.controller.PlayerController;
import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.panel.ControlBarPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MainFrameTest {

    @BeforeEach
    void setUp() {
        // Skip UI window instantiation in headless environments to prevent HeadlessException
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
    }

    @Test
    @DisplayName("MainFrame initializes 3-pane layout, minimum size, child panels, and shortcut manager")
    void testMainFrameInitialization() {
        MainFrame frame = new MainFrame();

        try {
            // Panels and split pane
            assertThat(frame.getControlBarPanel()).isNotNull();
            assertThat(frame.getSidebarPanel()).isNotNull();
            assertThat(frame.getTrackTablePanel()).isNotNull();
            assertThat(frame.getAlbumGridViewPanel()).isNotNull();
            assertThat(frame.getAlbumHeaderPanel()).isNotNull();
            assertThat(frame.getStatusBarPanel()).isNotNull();
            assertThat(frame.getSplitPane()).isNotNull();
            assertThat(frame.getSplitPane().getLeftComponent()).isSameAs(frame.getSidebarPanel());
            assertThat(frame.getSplitPane().getRightComponent()).isSameAs(frame.getMainContentPanel());
            assertThat(frame.getShortcutManager()).isNotNull();

            // Window properties
            assertThat(frame.getMinimumSize()).isEqualTo(new Dimension(800, 520));
            assertThat(frame.getTitle()).isEqualTo("Music Library Manager");
            assertThat(frame.getJMenuBar()).isNotNull();
            assertThat(frame.getAppMenuBar()).isNotNull().isSameAs(frame.getJMenuBar());
            assertThat(frame.getAudioPlayer()).isNotNull();
            assertThat(frame.getPlayerController()).isNotNull();
            assertThat(frame.getSearchIndex()).isNotNull();
            assertThat(frame.getSearchController()).isNotNull();
            assertThat(frame.getLibraryController()).isNotNull();
            assertThat(frame.getMiniPlayerFrame()).isNotNull();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("MainFrame menu bar hierarchy contains all required menus and submenus")
    void testMenuBarHierarchy() {
        MainFrame frame = new MainFrame();

        try {
            JMenuBar menuBar = frame.getJMenuBar();
            assertThat(menuBar.getMenuCount()).isEqualTo(6); // File, Edit, View, Controls, Account, Help

            JMenu fileMenu = menuBar.getMenu(0);
            assertThat(fileMenu.getText()).isEqualTo("File");
            assertThat(fileMenu.getItemCount()).isEqualTo(7); // Add File, Add Folder, Rescan Library, Sep, Rescan Missing, Sep, Exit

            JMenu editMenu = menuBar.getMenu(1);
            assertThat(editMenu.getText()).isEqualTo("Edit");

            JMenu viewMenu = menuBar.getMenu(2);
            assertThat(viewMenu.getText()).isEqualTo("View");
            assertThat(viewMenu.getItemCount()).isEqualTo(4); // Theme sub, Lang sub, Sep, Mini Player

            JMenu controlsMenu = menuBar.getMenu(3);
            assertThat(controlsMenu.getText()).isEqualTo("Controls");
            assertThat(controlsMenu.getItemCount()).isEqualTo(8); // Play, Pause, Sep, Next, Prev, Sep, Shuffle, Repeat
            assertThat(frame.getShuffleMenuItem()).isNotNull();
            assertThat(frame.getRepeatSubMenu()).isNotNull();
            assertThat(frame.getRepeatSubMenu().getItemCount()).isEqualTo(3); // Off, All, One

            JMenu accountMenu = menuBar.getMenu(4);
            assertThat(accountMenu.getText()).isEqualTo("Account");
            assertThat(frame.getAccountMenu()).isNotNull();
            assertThat(frame.getSwitchAccountItem()).isNotNull();

            JMenu helpMenu = menuBar.getMenu(5);
            assertThat(helpMenu.getText()).isEqualTo("Help");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Language change dynamically cascades to window title, menu items, and all child panels")
    void testDynamicLanguageUpdate() {
        MainFrame frame = new MainFrame();

        try {
            // 1. Switch to Japanese
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);

            assertThat(frame.getTitle()).isEqualTo("ミュージックライブラリマネージャー");
            JMenuBar menuBar = frame.getJMenuBar();
            assertThat(menuBar.getMenu(0).getText()).isEqualTo("ファイル");
            assertThat(menuBar.getMenu(1).getText()).isEqualTo("編集");
            assertThat(menuBar.getMenu(2).getText()).isEqualTo("表示");
            assertThat(menuBar.getMenu(3).getText()).isEqualTo("操作");
            assertThat(menuBar.getMenu(4).getText()).isEqualTo("アカウント");
            assertThat(menuBar.getMenu(5).getText()).isEqualTo("ヘルプ");
            assertThat(menuBar.getMenu(0).getItem(2).getText()).isEqualTo("ライブラリを一括再スキャン...");
            assertThat(menuBar.getMenu(0).getItem(4).getText()).isEqualTo("行方不明ファイルを再スキャン・再リンク...");
            assertThat(frame.getShuffleMenuItem().getText()).isEqualTo("シャッフル");
            assertThat(frame.getRepeatSubMenu().getText()).isEqualTo("リピート");

            // Child panel propagation
            assertThat(frame.getControlBarPanel().getPrevButton().getToolTipText()).isEqualTo("前の曲");
            assertThat(frame.getSidebarPanel().getNewPlaylistButton().getText()).contains("新規プレイリスト");
            assertThat(frame.getTrackTablePanel().getTable().getColumnModel().getColumn(2).getHeaderValue()).isEqualTo("タイトル");
            assertThat(frame.getStatusBarPanel().getStatusLabel().getText()).isEqualTo("準備完了");

            // 2. Switch back to English
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

            assertThat(frame.getTitle()).isEqualTo("Music Library Manager");
            assertThat(menuBar.getMenu(0).getText()).isEqualTo("File");
            assertThat(menuBar.getMenu(0).getItem(2).getText()).isEqualTo("Rescan Library...");
            assertThat(menuBar.getMenu(0).getItem(4).getText()).isEqualTo("Rescan & Relink Missing Files...");
            assertThat(menuBar.getMenu(2).getText()).isEqualTo("View");
            assertThat(frame.getShuffleMenuItem().getText()).isEqualTo("Shuffle");
            assertThat(frame.getRepeatSubMenu().getText()).isEqualTo("Repeat");
            assertThat(frame.getControlBarPanel().getPrevButton().getToolTipText()).isEqualTo("Previous Track");
            assertThat(frame.getSidebarPanel().getNewPlaylistButton().getText()).contains("New Playlist");
            assertThat(frame.getTrackTablePanel().getTable().getColumnModel().getColumn(2).getHeaderValue()).isEqualTo("Title");
            assertThat(frame.getStatusBarPanel().getStatusLabel().getText()).isEqualTo("Ready");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Theme change syncs radio menu item selection in View -> Theme menu")
    void testThemeSelectionSync() {
        MainFrame frame = new MainFrame();

        try {
            JMenu viewMenu = frame.getJMenuBar().getMenu(2);
            JMenu themeMenu = (JMenu) viewMenu.getItem(0);

            JRadioButtonMenuItem darkItem = (JRadioButtonMenuItem) themeMenu.getItem(0);
            JRadioButtonMenuItem lightItem = (JRadioButtonMenuItem) themeMenu.getItem(1);
            JRadioButtonMenuItem systemItem = (JRadioButtonMenuItem) themeMenu.getItem(2);

            ThemeManager.getInstance().applyTheme(AppTheme.LIGHT);
            assertThat(lightItem.isSelected()).isTrue();
            assertThat(darkItem.isSelected()).isFalse();

            ThemeManager.getInstance().applyTheme(AppTheme.DARK);
            assertThat(darkItem.isSelected()).isTrue();
            assertThat(lightItem.isSelected()).isFalse();

            ThemeManager.getInstance().applyTheme(AppTheme.SYSTEM);
            assertThat(systemItem.isSelected()).isTrue();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Keyboard shortcuts integrated in MainFrame control volume (+/-5%), theme toggle, and actions correctly")
    void testKeyboardShortcutsIntegration() {
        MainFrame frame = new MainFrame();

        try {
            ActionMap actionMap = frame.getRootPane().getActionMap();
            JSlider volumeSlider = frame.getControlBarPanel().getVolumeSlider();

            // 1. Volume Up (+5%)
            volumeSlider.setValue(70);
            triggerAction(actionMap, "volumeUp");
            assertThat(volumeSlider.getValue()).isEqualTo(75);

            // 2. Volume Down (-5%)
            triggerAction(actionMap, "volumeDown");
            assertThat(volumeSlider.getValue()).isEqualTo(70);

            // 3. Volume Up upper boundary (100)
            volumeSlider.setValue(98);
            triggerAction(actionMap, "volumeUp");
            assertThat(volumeSlider.getValue()).isEqualTo(100);

            // 4. Volume Down lower boundary (0)
            volumeSlider.setValue(3);
            triggerAction(actionMap, "volumeDown");
            assertThat(volumeSlider.getValue()).isEqualTo(0);

            // 5. Theme Toggle action
            ThemeManager.getInstance().applyTheme(AppTheme.DARK);
            triggerAction(actionMap, "themeToggle");
            assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
            triggerAction(actionMap, "themeToggle");
            assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.DARK);

            // 6. Search focus, play/pause, skip, mini player triggers
            assertThatCode(() -> {
                triggerAction(actionMap, "searchFocus");
                triggerAction(actionMap, "playPauseToggle");
                triggerAction(actionMap, "skipForwardMask");
                triggerAction(actionMap, "skipBackwardMask");
                triggerAction(actionMap, "miniPlayerToggle");
            }).doesNotThrowAnyException();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("dispose cleanly unregisters language and theme listeners and stops folder watcher and importer")
    void testDispose() {
        MainFrame frame = new MainFrame();

        assertThat(frame.getFolderWatcherService()).isNotNull();
        assertThat(frame.getImportService()).isNotNull();
        assertThatCode(frame::dispose).doesNotThrowAnyException();
        assertThat(frame.getFolderWatcherService().isRunning()).isFalse();
        assertThat(frame.getImportService().isShutdown()).isTrue();
    }

    @Test
    @DisplayName("importPaths safely ignores null or empty collections without throwing exceptions")
    void testImportPathsEmptyOrNullSafelyNoOp() {
        MainFrame frame = new MainFrame();

        try {
            assertThatCode(() -> {
                frame.importPaths(null, null);
                frame.importPaths(List.of(), null);
                frame.importFolder(null);
            }).doesNotThrowAnyException();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("refreshLibraryView updates track table panel and status bar summary")
    void testRefreshLibraryViewUpdatesComponents() {
        Library library = new Library();
        MainFrame frame = new MainFrame(library, null);

        try {
            assertThat(frame.getTrackTablePanel().getTrackCount()).isEqualTo(0);

            Track track = Track.builder()
                    .filePath("C:/Music/sync_song.mp3")
                    .title("Sync Song")
                    .durationSeconds(240)
                    .fileSizeBytes(4000)
                    .build();
            library.addTrack(track);

            frame.refreshLibraryView();

            assertThat(frame.getTrackTablePanel().getTrackCount()).isEqualTo(1);
            assertThat(frame.getStatusBarPanel().getSummaryLabel().getText()).contains("1");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("dispose cleanly releases PlayerController and AudioPlayer resources alongside watcher and importer")
    void testDisposeReleasesControllerAndAudioPlayer() {
        TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
        TrackingPlayerController trackingController = new TrackingPlayerController(trackingPlayer, null);
        MainFrame frame = new MainFrame(new Library(), null, trackingPlayer, trackingController);

        assertThat(trackingPlayer.closed).isFalse();
        assertThat(trackingController.disposedCalled).isFalse();

        frame.dispose();

        assertThat(trackingPlayer.closed).isTrue();
        assertThat(trackingController.disposedCalled).isTrue();
        assertThat(frame.getFolderWatcherService().isRunning()).isFalse();
        assertThat(frame.getImportService().isShutdown()).isTrue();
    }

    @Test
    @DisplayName("Controls menu items trigger play, pause, next, and previous actions on PlayerController")
    void testControlsMenuItemsDispatchPlaybackOperations() throws java.io.IOException {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("menu_song", ".mp3");
        try {
            Library library = new Library();
            Track track = Track.builder().id("1").title("Menu Song").filePath(tempFile.toString()).build();
            library.addTrack(track);

            TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
            MainFrame frame = new MainFrame(library, null, trackingPlayer, null);

            try {
                frame.refreshLibraryView();
                frame.getTrackTablePanel().getTable().setRowSelectionInterval(0, 0);

                JMenu controlsMenu = frame.getJMenuBar().getMenu(3); // Controls menu
                JMenuItem playItem = controlsMenu.getItem(0);
                JMenuItem pauseItem = controlsMenu.getItem(1);
                JMenuItem nextItem = controlsMenu.getItem(3); // index 2 is Separator
                JMenuItem prevItem = controlsMenu.getItem(4);

                // 1. Play with selected row
                playItem.doClick();
                assertThat(frame.getPlayerController().getAudioPlayer().getState()).isEqualTo(PlaybackState.PLAYING);
                assertThat(frame.getPlayerController().getAudioPlayer().getCurrentTrack()).isEqualTo(track);

                // 2. Pause
                pauseItem.doClick();
                assertThat(frame.getPlayerController().getAudioPlayer().getState()).isEqualTo(PlaybackState.PAUSED);

                // 3. Play again resumes
                playItem.doClick();
                assertThat(frame.getPlayerController().getAudioPlayer().getState()).isEqualTo(PlaybackState.PLAYING);

                // 4. Next and Previous do not throw exceptions
                assertThatCode(() -> {
                    nextItem.doClick();
                    prevItem.doClick();
                }).doesNotThrowAnyException();
            } finally {
                frame.dispose();
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("playPauseToggle shortcut plays selected track when stopped, or toggles state")
    void testPlayPauseToggleShortcutWithSelection() {
        Library library = new Library();
        Track track = Track.builder().id("2").title("Shortcut Song").filePath("shortcut.mp3").build();
        library.addTrack(track);

        TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
        MainFrame frame = new MainFrame(library, null, trackingPlayer, null);

        try {
            frame.refreshLibraryView();
            frame.getTrackTablePanel().getTable().setRowSelectionInterval(0, 0);

            ActionMap actionMap = frame.getRootPane().getActionMap();

            // When STOPPED and track is selected -> play(selected)
            triggerAction(actionMap, "playPauseToggle");
            assertThat(frame.getPlayerController().getAudioPlayer().getState()).isEqualTo(PlaybackState.PLAYING);
            assertThat(frame.getPlayerController().getAudioPlayer().getCurrentTrack()).isEqualTo(track);

            // When PLAYING -> pause()
            triggerAction(actionMap, "playPauseToggle");
            assertThat(frame.getPlayerController().getAudioPlayer().getState()).isEqualTo(PlaybackState.PAUSED);

            // When PAUSED -> resume()
            triggerAction(actionMap, "playPauseToggle");
            assertThat(frame.getPlayerController().getAudioPlayer().getState()).isEqualTo(PlaybackState.PLAYING);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Shuffle and repeat menu items synchronize state bi-directionally with PlayerController")
    void testControlsMenuShuffleAndRepeatInteraction() throws Exception {
        MainFrame frame = new MainFrame();

        try {
            // 1. Menu item click drives PlayerController
            assertThat(frame.getPlayerController().isShuffle()).isFalse();
            frame.getShuffleMenuItem().doClick();
            assertThat(frame.getPlayerController().isShuffle()).isTrue();

            assertThat(frame.getPlayerController().getRepeatMode()).isEqualTo(RepeatMode.OFF);
            frame.getRepeatAllItem().doClick();
            assertThat(frame.getPlayerController().getRepeatMode()).isEqualTo(RepeatMode.ALL);

            frame.getRepeatOneItem().doClick();
            assertThat(frame.getPlayerController().getRepeatMode()).isEqualTo(RepeatMode.ONE);

            frame.getRepeatOffItem().doClick();
            assertThat(frame.getPlayerController().getRepeatMode()).isEqualTo(RepeatMode.OFF);

            // 2. PlayerController programmatic change updates menu selection
            frame.getPlayerController().setShuffle(false);
            if (!GraphicsEnvironment.isHeadless()) {
                javax.swing.SwingUtilities.invokeAndWait(() -> {});
            }
            assertThat(frame.getShuffleMenuItem().isSelected()).isFalse();

            frame.getPlayerController().setRepeatMode(RepeatMode.ALL);
            if (!GraphicsEnvironment.isHeadless()) {
                javax.swing.SwingUtilities.invokeAndWait(() -> {});
            }
            assertThat(frame.getRepeatAllItem().isSelected()).isTrue();
            assertThat(frame.getRepeatOffItem().isSelected()).isFalse();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Context menu 'Play Next' action is correctly wired to PlayerController priority queue")
    void testContextMenuPlayNextWiredToPriorityQueue() throws java.io.IOException {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("next_track", ".mp3");
        try {
            Library library = new Library();
            Track track = Track.builder().id("next_1").title("Next Track").filePath(tempFile.toString()).build();
            library.addTrack(track);

            MainFrame frame = new MainFrame(library, null);

            try {
                frame.refreshLibraryView();
                frame.getTrackTablePanel().getTable().setRowSelectionInterval(0, 0);

                // Context menu component 1 is playNextMenuItem
                javax.swing.JMenuItem playNextItem = (javax.swing.JMenuItem) frame.getTrackTablePanel().getContextMenu().getComponent(1);
                playNextItem.doClick();

                assertThat(frame.getPlayerController().getQueue().getPriorityQueue()).contains(track);
            } finally {
                frame.dispose();
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("Dynamic language switch cascades accurately to Controls shuffle and repeat menu items")
    void testControlsMenuLanguageSwitch() {
        MainFrame frame = new MainFrame();

        try {
            // Japanese switch
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            frame.updateLanguage();

            assertThat(frame.getShuffleMenuItem().getText()).isEqualTo("シャッフル");
            assertThat(frame.getRepeatSubMenu().getText()).isEqualTo("リピート");
            assertThat(frame.getRepeatOffItem().getText()).isEqualTo("リピートオフ");
            assertThat(frame.getRepeatAllItem().getText()).isEqualTo("全曲リピート");
            assertThat(frame.getRepeatOneItem().getText()).isEqualTo("1曲リピート");

            // English switch
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            frame.updateLanguage();

            assertThat(frame.getShuffleMenuItem().getText()).isEqualTo("Shuffle");
            assertThat(frame.getRepeatSubMenu().getText()).isEqualTo("Repeat");
            assertThat(frame.getRepeatOffItem().getText()).isEqualTo("Repeat Off");
            assertThat(frame.getRepeatAllItem().getText()).isEqualTo("Repeat All");
            assertThat(frame.getRepeatOneItem().getText()).isEqualTo("Repeat One");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("i18n property files (default, en, ja) have completely synchronized keys including all Phase 4.4 keys")
    void testI18nPropertiesKeySynchronization() throws Exception {
        java.util.Properties defaultProps = loadProperties("/messages.properties");
        java.util.Properties enProps = loadProperties("/messages_en.properties");
        java.util.Properties jaProps = loadProperties("/messages_ja.properties");

        // Required Phase 4.4 keys
        List<String> requiredPhase44Keys = List.of(
                "menu.controls.shuffle",
                "menu.controls.repeat",
                "menu.controls.repeat_off",
                "menu.controls.repeat_all",
                "menu.controls.repeat_one",
                "control.shuffle",
                "control.repeat_off",
                "control.repeat_all",
                "control.repeat_one"
        );

        for (String key : requiredPhase44Keys) {
            assertThat(defaultProps).containsKey(key);
            assertThat(enProps).containsKey(key);
            assertThat(jaProps).containsKey(key);
        }

        // Required Phase 5.2 Playlist & Sidebar keys
        List<String> requiredPhase52Keys = List.of(
                "sidebar.library",
                "sidebar.playlists",
                "sidebar.all_songs",
                "sidebar.artists",
                "sidebar.albums",
                "sidebar.recently_added",
                "sidebar.new_playlist",
                "sidebar.playlist.play",
                "sidebar.playlist.rename",
                "sidebar.playlist.delete",
                "sidebar.playlist.default_name",
                "sidebar.playlist.new_prompt",
                "sidebar.playlist.rename_prompt",
                "sidebar.playlist.confirm_delete",
                "table.menu.delete_from_playlist"
        );

        for (String key : requiredPhase52Keys) {
            assertThat(defaultProps).as("Key in messages.properties: " + key).containsKey(key);
            assertThat(enProps).as("Key in messages_en.properties: " + key).containsKey(key);
            assertThat(jaProps).as("Key in messages_ja.properties: " + key).containsKey(key);
        }

        // Required Phase 5.3 Mini Player keys
        List<String> requiredPhase53Keys = List.of(
                "mini_player.title",
                "mini_player.pin",
                "mini_player.unpin",
                "mini_player.restore"
        );

        for (String key : requiredPhase53Keys) {
            assertThat(defaultProps).as("Key in messages.properties: " + key).containsKey(key);
            assertThat(enProps).as("Key in messages_en.properties: " + key).containsKey(key);
            assertThat(jaProps).as("Key in messages_ja.properties: " + key).containsKey(key);
        }

        // Complete 100% key parity
        assertThat(defaultProps.keySet()).isEqualTo(enProps.keySet());
        assertThat(enProps.keySet()).isEqualTo(jaProps.keySet());
    }

    @Test
    @DisplayName("FastSearchIndex and SearchController filter TrackTablePanel and update status summary in MainFrame")
    void testSearchIntegrationInMainFrame() {
        Library library = new Library();
        Track track1 = Track.builder()
                .id("s1")
                .filePath("s1.mp3")
                .title("Clair de Lune")
                .artist("Debussy")
                .genre("Classical")
                .build();
        Track track2 = Track.builder()
                .id("s2")
                .filePath("s2.mp3")
                .title("Moonlight Sonata")
                .artist("Beethoven")
                .genre("Classical")
                .build();
        library.addTrack(track1);
        library.addTrack(track2);

        MainFrame frame = new MainFrame(library, null);

        try {
            frame.refreshLibraryView();
            assertThat(frame.getTrackTablePanel().getRowSorter().getViewRowCount()).isEqualTo(2);

            // 1. Type query in search field
            frame.getControlBarPanel().getSearchField().setText("debussy");
            assertThat(frame.getTrackTablePanel().getRowSorter().getViewRowCount()).isEqualTo(1);
            assertThat(frame.getStatusBarPanel().getSummaryLabel().getText()).contains("1");

            // 2. Clear query
            frame.getControlBarPanel().getSearchField().setText("");
            assertThat(frame.getTrackTablePanel().getRowSorter().getViewRowCount()).isEqualTo(2);
            assertThat(frame.getStatusBarPanel().getSummaryLabel().getText()).contains("2");
        } finally {
            frame.dispose();
            assertThat(frame.getSearchController().isDisposed()).isTrue();
        }
    }

    @Test
    @DisplayName("Search filter accurately recalculates status bar summary (count, duration, size) in real time")
    void testStatusBarSummaryRecalculationUnderSearchFilter() {
        Library library = new Library();
        Track trackA = Track.builder()
                .id("a")
                .filePath("a.mp3")
                .title("Symphony 1")
                .artist("Brahms")
                .durationSeconds(300)
                .fileSizeBytes(4_000_000)
                .build();
        Track trackB = Track.builder()
                .id("b")
                .filePath("b.mp3")
                .title("Concerto 2")
                .artist("Rachmaninoff")
                .durationSeconds(600)
                .fileSizeBytes(8_000_000)
                .build();
        library.addTrack(trackA);
        library.addTrack(trackB);

        MainFrame frame = new MainFrame(library, null);

        try {
            frame.refreshLibraryView();
            // Both tracks: 2 items, 900s (15 min 0 sec)
            String fullSummary = frame.getStatusBarPanel().getSummaryLabel().getText();
            assertThat(fullSummary).contains("2 tracks").contains("15 min").contains("11.4 MB");

            // Filter to track A only
            frame.getControlBarPanel().getSearchField().setText("Brahms");
            String filteredSummary = frame.getStatusBarPanel().getSummaryLabel().getText();
            assertThat(filteredSummary).contains("1 tracks").contains("5 min").contains("3.8 MB");

            // Reset search filter
            frame.getControlBarPanel().getSearchField().setText("");
            String restoredSummary = frame.getStatusBarPanel().getSummaryLabel().getText();
            assertThat(restoredSummary).isEqualTo(fullSummary);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Dynamically added tracks automatically sync into FastSearchIndex and become searchable")
    void testDynamicTrackAdditionUpdatesSearchIndex() {
        Library library = new Library();
        MainFrame frame = new MainFrame(library, null);

        try {
            frame.refreshLibraryView();
            assertThat(frame.getSearchIndex().size()).isEqualTo(0);

            // Dynamically add a new track to library
            Track newTrack = Track.builder()
                    .id("dyn-1")
                    .filePath("dyn.mp3")
                    .title("The Rite of Spring")
                    .artist("Stravinsky")
                    .genre("Classical")
                    .build();
            library.addTrack(newTrack);
            frame.refreshLibraryView();

            assertThat(frame.getSearchIndex().size()).isEqualTo(1);

            // Search for newly added track
            frame.getControlBarPanel().getSearchField().setText("Stravinsky");
            assertThat(frame.getTrackTablePanel().getRowSorter().getViewRowCount()).isEqualTo(1);
            assertThat(frame.getTrackTablePanel().getTableModel().getTrackAt(0)).isEqualTo(newTrack);
        } finally {
            frame.dispose();
        }
    }

    private java.util.Properties loadProperties(String resourcePath) throws Exception {
        java.util.Properties props = new java.util.Properties();
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            assertThat(is).as("Resource exists: " + resourcePath).isNotNull();
            props.load(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
        }
        return props;
    }

    private void triggerAction(ActionMap actionMap, String key) {
        Action action = actionMap.get(key);
        assertThat(action).as("Action for key: " + key).isNotNull();
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, key));
    }

    @Test
    @DisplayName("MainFrame coordinates LibraryController for playlist management and table updates")
    void testLibraryControllerIntegration() {
        Library lib = new Library();
        Track t1 = Track.builder()
                .id("track-1")
                .filePath("tracks/track1.mp3")
                .title("Song 1")
                .artist("Artist 1")
                .album("Album 1")
                .build();
        lib.addTrack(t1);

        MainFrame frame = new MainFrame(lib, null);
        try {
            var libController = frame.getLibraryController();
            assertThat(libController).isNotNull();

            // Create playlist via controller
            Playlist playlist = libController.createPlaylist("Favorites");
            assertThat(frame.getSidebarPanel().getCurrentPlaylists()).contains(playlist);
            assertThat(libController.getCurrentPlaylistId()).isEqualTo(playlist.getId());

            // Add track to playlist
            libController.addTracksToPlaylist(playlist.getId(), List.of("track-1"));
            assertThat(frame.getTrackTablePanel().getTableModel().getTracks()).contains(t1);
            assertThat(libController.isInPlaylistContext()).isTrue();

            // Switch to All Songs section
            frame.getSidebarPanel().selectSection(com.musiclibrary.ui.panel.SidebarPanel.LibrarySection.ALL_SONGS);
            assertThat(libController.getCurrentSection()).isEqualTo(com.musiclibrary.ui.panel.SidebarPanel.LibrarySection.ALL_SONGS);
            assertThat(libController.isInPlaylistContext()).isFalse();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("MainFrame.dispose disposes LibraryController")
    void testLibraryControllerDisposedOnFrameDispose() {
        MainFrame frame = new MainFrame();
        var libController = frame.getLibraryController();
        assertThat(libController.isDisposed()).isFalse();

        frame.dispose();
        assertThat(libController.isDisposed()).isTrue();
    }

    @Test
    @DisplayName("switchToMiniPlayer hides MainFrame and shows MiniPlayerFrame; restore reverses state")
    void testSwitchToMiniPlayerAndRestore() {
        MainFrame frame = new MainFrame();
        try {
            frame.setVisible(true);
            assertThat(frame.isVisible()).isTrue();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isFalse();

            // Switch to mini player
            frame.switchToMiniPlayer();
            assertThat(frame.isVisible()).isFalse();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isTrue();

            // Restore main frame
            frame.restoreFromMiniPlayer();
            assertThat(frame.isVisible()).isTrue();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isFalse();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("MainFrame.dispose disposes MiniPlayerFrame")
    void testMiniPlayerFrameDisposedOnMainFrameDispose() {
        MainFrame frame = new MainFrame();
        var miniFrame = frame.getMiniPlayerFrame();
        assertThat(miniFrame.isDisposed()).isFalse();

        frame.dispose();
        assertThat(miniFrame.isDisposed()).isTrue();
    }

    @Test
    @DisplayName("Delete key triggers context-aware deletion in MainFrame: playlist-only when in playlist, full cascade when in all-songs")
    void testDeleteKeyTriggersContextAwareDeletionInMainFrame() {
        Library library = new Library();
        Track track1 = Track.builder().id("mf-t1").title("Track One").artist("Artist").filePath("1.mp3").build();
        Track track2 = Track.builder().id("mf-t2").title("Track Two").artist("Artist").filePath("2.mp3").build();
        library.addTrack(track1);
        library.addTrack(track2);

        MainFrame frame = new MainFrame(library, null);

        try {
            var libController = frame.getLibraryController();
            Playlist playlist = libController.createPlaylist("Deletion Test Mix");
            libController.addTracksToPlaylist(playlist.getId(), List.of("mf-t1", "mf-t2"));

            // --- 1. In Playlist context ---
            libController.showPlaylist(playlist.getId());
            assertThat(libController.isInPlaylistContext()).isTrue();
            assertThat(frame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track1, track2);

            // Select track 1 (row 0)
            frame.getTrackTablePanel().getTable().setRowSelectionInterval(0, 0);

            // Trigger Delete key action
            javax.swing.Action deleteAction = frame.getTrackTablePanel().getTable().getActionMap().get("deleteSelectedTracks");
            assertThat(deleteAction).isNotNull();
            deleteAction.actionPerformed(new ActionEvent(frame.getTrackTablePanel().getTable(), ActionEvent.ACTION_PERFORMED, "deleteSelectedTracks"));

            // Playlist should now only contain track 2
            assertThat(playlist.getTrackIds()).containsExactly("mf-t2");
            assertThat(frame.getTrackTablePanel().getTableModel().getTracks()).containsExactly(track2);

            // Library domain and search index MUST still retain track 1
            assertThat(library.getTrack("mf-t1")).isNotNull();
            assertThat(frame.getSearchIndex().search("Track One")).contains("mf-t1");

            // --- 2. In ALL_SONGS context ---
            frame.getSidebarPanel().selectSection(com.musiclibrary.ui.panel.SidebarPanel.LibrarySection.ALL_SONGS);
            assertThat(libController.isInPlaylistContext()).isFalse();

            // Select track 2 in table
            int rowT2 = -1;
            for (int i = 0; i < frame.getTrackTablePanel().getTableModel().getRowCount(); i++) {
                if ("mf-t2".equals(frame.getTrackTablePanel().getTableModel().getTrackAt(i).getId())) {
                    rowT2 = i;
                    break;
                }
            }
            assertThat(rowT2).isNotEqualTo(-1);
            frame.getTrackTablePanel().getTable().setRowSelectionInterval(rowT2, rowT2);

            // Trigger Delete key action
            deleteAction.actionPerformed(new ActionEvent(frame.getTrackTablePanel().getTable(), ActionEvent.ACTION_PERFORMED, "deleteSelectedTracks"));

            // Library domain MUST have removed track 2
            assertThat(library.getTrack("mf-t2")).isNull();

            // Search index MUST have removed track 2
            assertThat(frame.getSearchIndex().search("Track Two")).isEmpty();

            // Playlist MUST cascade remove track 2
            assertThat(playlist.getTrackIds()).doesNotContain("mf-t2");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Sidebar playlist list has DropTarget installed and accepts track drag and drop")
    void testSidebarDropTargetIntegration() {
        Library library = new Library();
        Track track = Track.builder().id("drop-t1").title("Drop Target Song").filePath("drop.mp3").build();
        library.addTrack(track);

        MainFrame frame = new MainFrame(library, null);

        try {
            var libController = frame.getLibraryController();
            Playlist targetPlaylist = libController.createPlaylist("D&D Target");
            com.musiclibrary.util.SwingUtils.runOnEdtAndWait(() -> {});

            // Drop target on playlist list
            var dropTarget = frame.getSidebarPanel().getPlaylistList().getDropTarget();
            assertThat(dropTarget).isNotNull();

            // Simulate dropping track ID onto index 0 (targetPlaylist)
            int index = frame.getSidebarPanel().getCurrentPlaylists().indexOf(targetPlaylist);
            assertThat(index).isNotEqualTo(-1);

            // Directly invoke addTracksToPlaylist which the drop callback delegates to
            libController.addTracksToPlaylist(targetPlaylist.getId(), List.of("drop-t1"));
            assertThat(targetPlaylist.getTrackIds()).contains("drop-t1");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("switchToMiniPlayer and restoreFromMiniPlayer toggle window visibilities seamlessly")
    void testMiniPlayerSwitchAndRestore() {
        MainFrame frame = new MainFrame();

        try {
            frame.setVisible(true);
            assertThat(frame.isVisible()).isTrue();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isFalse();

            // 1. Switch to mini player
            frame.switchToMiniPlayer();
            assertThat(frame.isVisible()).isFalse();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isTrue();

            // 2. Restore to main frame
            frame.restoreFromMiniPlayer();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isFalse();
            assertThat(frame.isVisible()).isTrue();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Mini player mode can be triggered via ControlBar button, View menu item, and shortcut")
    void testMiniPlayerTriggers() {
        MainFrame frame = new MainFrame();

        try {
            frame.setVisible(true);

            // 1. Trigger via ControlBar button
            frame.getControlBarPanel().getMiniPlayerButton().doClick();
            assertThat(frame.isVisible()).isFalse();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isTrue();

            frame.restoreFromMiniPlayer();
            assertThat(frame.isVisible()).isTrue();

            // 2. Trigger via View Menu item (View menu index 2, item index 3 is miniPlayerItem)
            JMenuItem miniPlayerMenuItem = (JMenuItem) frame.getJMenuBar().getMenu(2).getItem(3);
            assertThat(miniPlayerMenuItem.getText()).isEqualTo("Mini Player");
            miniPlayerMenuItem.doClick();
            assertThat(frame.isVisible()).isFalse();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isTrue();

            frame.restoreFromMiniPlayer();
            assertThat(frame.isVisible()).isTrue();

            // 3. Trigger via Keyboard Shortcut ActionMap
            ActionMap actionMap = frame.getRootPane().getActionMap();
            triggerAction(actionMap, "miniPlayerToggle");
            assertThat(frame.isVisible()).isFalse();
            assertThat(frame.getMiniPlayerFrame().isVisible()).isTrue();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("MainFrame.dispose also disposes MiniPlayerFrame")
    void testMiniPlayerDisposedOnMainFrameDispose() {
        MainFrame frame = new MainFrame();
        var miniPlayer = frame.getMiniPlayerFrame();
        assertThat(miniPlayer).isNotNull();
        assertThat(miniPlayer.isDisposed()).isFalse();

        frame.dispose();
        assertThat(miniPlayer.isDisposed()).isTrue();
    }

    @Test
    @DisplayName("playTrackSafely and enqueueNextSafely handle null or valid tracks safely")
    void testPlayTrackSafely() throws java.io.IOException {
        TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
        MainFrame frame = new MainFrame(new Library(), null, trackingPlayer, null);
        try {
            // 1. Null track safe handling (no-op)
            assertThatCode(() -> frame.playTrackSafely(null)).doesNotThrowAnyException();
            assertThatCode(() -> frame.enqueueNextSafely(null)).doesNotThrowAnyException();
            assertThat(trackingPlayer.getCurrentTrack()).isNull();

            // 2. Valid track playback delegated safely to player
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test_track", ".mp3");
            try {
                Track validTrack = Track.builder()
                        .id("valid-1")
                        .title("Valid Song")
                        .filePath(tempFile.toString())
                        .build();

                frame.playTrackSafely(validTrack);
                assertThat(trackingPlayer.getCurrentTrack()).isEqualTo(validTrack);

                frame.enqueueNextSafely(validTrack);
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("playTrackSafely blocks playback of missing tracks and sets missing flag")
    void testPlayTrackSafelyBlocksMissingTrack() {
        TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
        MainFrame frame = new MainFrame(new Library(), null, trackingPlayer, null);
        try {
            Track missingTrack = Track.builder()
                    .id("missing-play-1")
                    .title("Non-Existent Audio")
                    .filePath("C:/does_not_exist/file.mp3")
                    .build();
            assertThat(missingTrack.isMissing()).isFalse();

            // Play track safely - should detect file does not exist, set isMissing=true, and NOT play
            frame.playTrackSafely(missingTrack);
            assertThat(missingTrack.isMissing()).isTrue();
            assertThat(trackingPlayer.getCurrentTrack()).isNull();
            assertThat(trackingPlayer.isPlaying()).isFalse();

            // Enqueue next safely also marks missing
            missingTrack.setMissing(false);
            frame.enqueueNextSafely(missingTrack);
            assertThat(missingTrack.isMissing()).isTrue();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("rescanAndRelinkMissing safely returns when library has no missing tracks")
    void testRescanAndRelinkMissingWhenNoMissingTracks() throws java.io.IOException {
        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("existing_song", ".mp3");
        try {
            Library lib = new Library();
            Track track = Track.builder().id("exist-1").filePath(tempFile.toString()).build();
            lib.addTrack(track);

            MainFrame frame = new MainFrame(lib, null);
            try {
                // When frame is not showing, JOptionPane is skipped, method safely returns
                assertThatCode(frame::rescanAndRelinkMissing).doesNotThrowAnyException();
                assertThat(track.isMissing()).isFalse();
            } finally {
                frame.dispose();
            }
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("Edit -> Edit Tags menu item has accelerator Ctrl+I and updates text on locale change")
    void testEditTagsMenuItem_HierarchyAndLocalization() {
        MainFrame frame = new MainFrame();
        try {
            JMenuItem editTagsItem = frame.getEditTagsItem();
            assertThat(editTagsItem).isNotNull();
            assertThat(editTagsItem.getText()).isEqualTo("Edit Tags...");
            assertThat(editTagsItem.getAccelerator()).isNotNull();

            // Switch to Japanese
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            assertThat(editTagsItem.getText()).isEqualTo("タグを編集...");

            // Switch back to English
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            assertThat(editTagsItem.getText()).isEqualTo("Edit Tags...");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("openTagEditorForSelection safely no-ops without exception when no tracks are selected")
    void testOpenTagEditorForSelection_NoSelectionDoesNotThrow() {
        MainFrame frame = new MainFrame();
        try {
            assertThatCode(frame::openTagEditorForSelection).doesNotThrowAnyException();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("openTagEditorForTracks safely handles null or empty track lists without exceptions")
    void testOpenTagEditorForTracksSafelyNoOpsOnNullOrEmpty() {
        MainFrame frame = new MainFrame();
        try {
            assertThatCode(() -> frame.openTagEditorForTracks(null)).doesNotThrowAnyException();
            assertThatCode(() -> frame.openTagEditorForTracks(List.of())).doesNotThrowAnyException();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Rescan library menu item is configured with accelerator, localized text, and rescanFolder null safety")
    void testRescanLibraryMenuItemConfiguration() {
        MainFrame frame = new MainFrame();
        try {
            JMenuItem rescanItem = frame.getRescanLibraryItem();
            assertThat(rescanItem).isNotNull();
            assertThat(rescanItem.getText()).isEqualTo("Rescan Library...");
            assertThat(rescanItem.getAccelerator()).isEqualTo(
                    javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                            java.awt.event.KeyEvent.CTRL_DOWN_MASK | java.awt.event.KeyEvent.SHIFT_DOWN_MASK)
            );

            // Localized text switch
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            frame.updateLanguage();
            assertThat(rescanItem.getText()).isEqualTo("ライブラリを一括再スキャン...");

            // Switch back
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            frame.updateLanguage();
            assertThat(rescanItem.getText()).isEqualTo("Rescan Library...");

            // Null safety
            assertThatCode(() -> frame.rescanFolder(null)).doesNotThrowAnyException();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("File menu hierarchy places Rescan Library at exact index 2 with Ctrl+Shift+R accelerator")
    void testFileMenuHierarchyAndRescanItemPosition() {
        MainFrame frame = new MainFrame();
        try {
            JMenu fileMenu = frame.getJMenuBar().getMenu(0);
            assertThat(fileMenu).isNotNull();
            assertThat(fileMenu.getText()).isEqualTo("File");

            // Index 0: Add File
            JMenuItem addFile = fileMenu.getItem(0);
            assertThat(addFile.getText()).isEqualTo("Add File...");
            assertThat(addFile.getAccelerator()).isEqualTo(
                    javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.KeyEvent.CTRL_DOWN_MASK)
            );

            // Index 1: Add Folder
            JMenuItem addFolder = fileMenu.getItem(1);
            assertThat(addFolder.getText()).isEqualTo("Add Folder...");

            // Index 2: Rescan Library (Ctrl+Shift+R)
            JMenuItem rescanLib = fileMenu.getItem(2);
            assertThat(rescanLib.getText()).isEqualTo("Rescan Library...");
            assertThat(rescanLib.getAccelerator()).isEqualTo(
                    javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R,
                            java.awt.event.KeyEvent.CTRL_DOWN_MASK | java.awt.event.KeyEvent.SHIFT_DOWN_MASK)
            );
            assertThat(rescanLib).isSameAs(frame.getRescanLibraryItem());

            // Index 4: Rescan Missing Tracks
            JMenuItem rescanMissing = fileMenu.getItem(4);
            assertThat(rescanMissing.getText()).isEqualTo("Rescan & Relink Missing Files...");

            // Index 6: Exit
            JMenuItem exit = fileMenu.getItem(6);
            assertThat(exit.getText()).isEqualTo("Exit");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Rescan dialog and toast localization keys exist and format accurately in English and Japanese")
    void testRescanLocalizationKeys() {
        I18nManager i18n = I18nManager.getInstance();

        // English
        i18n.setLocale(AppLocale.ENGLISH);
        assertThat(i18n.get("menu.file.rescan_library")).isEqualTo("Rescan Library...");
        assertThat(i18n.get("dialog.rescan.title")).isEqualTo("Rescan & Update Library");
        assertThat(i18n.format("toast.rescan_detail", 5, 2)).isEqualTo("5 tracks updated, 2 tracks added");

        // Japanese
        i18n.setLocale(AppLocale.JAPANESE);
        assertThat(i18n.get("menu.file.rescan_library")).isEqualTo("ライブラリを一括再スキャン...");
        assertThat(i18n.get("dialog.rescan.title")).isEqualTo("ライブラリの再スキャン・更新");
        assertThat(i18n.format("toast.rescan_detail", 5, 2)).isEqualTo("5 曲を更新、2 曲を追加しました");

        // Restore English
        i18n.setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("windowClosing event stops audioPlayer immediately")
    void testWindowClosingStopsAudioImmediately() {
        TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
        trackingPlayer.play(Track.builder().id("1").title("Test").filePath("dummy.mp3").build());
        assertThat(trackingPlayer.isPlaying()).isTrue();

        MainFrame frame = new MainFrame(new Library(), null, trackingPlayer, null);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        try {
            frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
            assertThat(trackingPlayer.isPlaying()).isFalse();
            assertThat(trackingPlayer.stopCallCount).isGreaterThan(0);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Exit menu action stops audioPlayer immediately and dispatches windowClosing")
    void testExitMenuActionStopsAudioImmediately() {
        TrackingAudioPlayer trackingPlayer = new TrackingAudioPlayer();
        trackingPlayer.play(Track.builder().id("1").title("Test").filePath("dummy.mp3").build());
        assertThat(trackingPlayer.isPlaying()).isTrue();

        MainFrame frame = new MainFrame(new Library(), null, trackingPlayer, null);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        try {
            frame.getAppMenuBar().getExitItem().doClick();
            assertThat(trackingPlayer.isPlaying()).isFalse();
            assertThat(trackingPlayer.stopCallCount).isGreaterThan(0);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("windowClosing and Exit menu action handle null audioPlayer safely without throwing")
    void testWindowClosingWithNullAudioPlayerSafelyIgnored() {
        // MainFrame with null audio player
        MainFrame frame = new MainFrame(new Library(), null, null, null);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        try {
            // WINDOW_CLOSING event with null player
            assertThatCode(() -> frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)))
                    .doesNotThrowAnyException();

            // Exit menu item click with null player
            assertThatCode(() -> frame.getAppMenuBar().getExitItem().doClick())
                    .doesNotThrowAnyException();
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("windowClosing safely catches and logs exceptions thrown by audioPlayer.stop()")
    void testWindowClosingHandlesAudioPlayerStopExceptionSafely() {
        TrackingAudioPlayer throwingPlayer = new TrackingAudioPlayer() {
            @Override
            public void stop() {
                super.stop();
                throw new RuntimeException("Simulated audio stop failure");
            }
        };

        MainFrame frame = new MainFrame(new Library(), null, throwingPlayer, null);
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        try {
            assertThatCode(() -> frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)))
                    .doesNotThrowAnyException();
            assertThat(throwingPlayer.stopCallCount).isGreaterThan(0);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("MainFrame syncs audio player events with TrackTablePanel playback status indicator")
    void testAudioPlayerSyncWithTrackTablePanel() {
        TrackingAudioPlayer customPlayer = new TrackingAudioPlayer();
        MainFrame frame = new MainFrame(new Library(), null, customPlayer, null);
        try {
            Track track = Track.builder().id("track-abc").title("Test Song").filePath("test.mp3").build();
            frame.getTrackTablePanel().setTracks(List.of(track));

            assertThat(frame.getTrackTablePanel().getCurrentPlayingTrackId()).isNull();
            assertThat(frame.getTrackTablePanel().getCurrentPlaybackState()).isEqualTo(PlaybackState.STOPPED);

            // Play track
            customPlayer.play(track);
            assertThat(frame.getTrackTablePanel().getCurrentPlayingTrackId()).isEqualTo("track-abc");
            assertThat(frame.getTrackTablePanel().getCurrentPlaybackState()).isEqualTo(PlaybackState.PLAYING);

            JLabel playingCell = (JLabel) frame.getTrackTablePanel().getTable().getCellRenderer(0, 0)
                    .getTableCellRendererComponent(frame.getTrackTablePanel().getTable(), 1, false, false, 0, 0);
            assertThat(playingCell.getIcon()).isNotNull();
            assertThat(playingCell.getText()).isEmpty();

            // Pause
            customPlayer.pause();
            assertThat(frame.getTrackTablePanel().getCurrentPlayingTrackId()).isEqualTo("track-abc");
            assertThat(frame.getTrackTablePanel().getCurrentPlaybackState()).isEqualTo(PlaybackState.PAUSED);

            JLabel pausedStatusCell = (JLabel) frame.getTrackTablePanel().getTable().getCellRenderer(0, 0)
                    .getTableCellRendererComponent(frame.getTrackTablePanel().getTable(), "", false, false, 0, 0);
            assertThat(pausedStatusCell.getIcon()).isNotNull();
            assertThat(pausedStatusCell.getText()).isEmpty();

            // Resume
            customPlayer.resume();
            assertThat(frame.getTrackTablePanel().getCurrentPlayingTrackId()).isEqualTo("track-abc");
            assertThat(frame.getTrackTablePanel().getCurrentPlaybackState()).isEqualTo(PlaybackState.PLAYING);

            // Stop
            customPlayer.stop();
            assertThat(frame.getTrackTablePanel().getCurrentPlayingTrackId()).isNull();
            assertThat(frame.getTrackTablePanel().getCurrentPlaybackState()).isEqualTo(PlaybackState.STOPPED);

            JLabel stoppedStatusCell = (JLabel) frame.getTrackTablePanel().getTable().getCellRenderer(0, 0)
                    .getTableCellRendererComponent(frame.getTrackTablePanel().getTable(), "", false, false, 0, 0);
            assertThat(stoppedStatusCell.getIcon()).isNull();
            assertThat(stoppedStatusCell.getText()).isEmpty();

            JLabel trackNumCell = (JLabel) frame.getTrackTablePanel().getTable().getCellRenderer(0, 1)
                    .getTableCellRendererComponent(frame.getTrackTablePanel().getTable(), 1, false, false, 0, 1);
            assertThat(trackNumCell.getIcon()).isNull();
            assertThat(trackNumCell.getText()).isEqualTo("1");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Opening album detail saves scroll position, and returning restores previous scroll offset")
    void testAlbumDetailNavigationPreservesScrollPosition() {
        Library library = new Library();
        for (int i = 0; i < 40; i++) {
            Track t = Track.builder()
                    .id("trk-" + i)
                    .filePath("trk_" + i + ".mp3")
                    .title("Song " + i)
                    .artist("Artist " + i)
                    .album("Album " + i)
                    .build();
            library.addTrack(t);
        }

        MainFrame frame = new MainFrame(library, null);
        try {
            frame.setSize(800, 600);
            frame.showAlbumGrid(false);
            frame.doLayout();

            var grid = frame.getAlbumGridViewPanel();
            grid.setSize(800, 500);
            grid.getScrollPane().setSize(800, 500);
            grid.getScrollPane().doLayout();

            int max = grid.getScrollPane().getVerticalScrollBar().getMaximum() - grid.getScrollPane().getVerticalScrollBar().getVisibleAmount();
            int testScrollOffset = Math.max(20, max / 2);
            grid.getScrollPane().getVerticalScrollBar().setValue(testScrollOffset);

            var albums = frame.getOrComputeAlbums();
            assertThat(albums).isNotEmpty();
            var targetAlbum = albums.get(5);

            // Open album detail
            frame.openAlbumDetail(targetAlbum);
            assertThat(grid.getSavedScrollPosition()).isEqualTo(testScrollOffset);
            assertThat(grid.getLastSelectedAlbum()).isEqualTo(targetAlbum);
            assertThat(frame.getAlbumHeaderPanel().isVisible()).isTrue();

            // Simulate returning from album detail (clicking back button)
            frame.getAlbumHeaderPanel().getBackButton().doClick();

            // Should be back to album grid and scroll position restored
            assertThat(frame.getAlbumHeaderPanel().isVisible()).isFalse();
            assertThat(grid.getScrollPane().getVerticalScrollBar().getValue()).isEqualTo(testScrollOffset);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("MainFrame updateLanguage cascades to track table Track# header in English and Japanese with 80px width")
    void testTrackNumberHeaderLanguageSwitchInMainFrame() {
        MainFrame frame = new MainFrame();

        try {
            var table = frame.getTrackTablePanel().getTable();
            var col = table.getColumnModel().getColumn(1);

            // English default
            assertThat(col.getHeaderValue()).isEqualTo("#");
            assertThat(col.getPreferredWidth()).isEqualTo(80);

            // Switch to Japanese
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            frame.updateLanguage();

            assertThat(col.getHeaderValue()).isEqualTo("トラック番号");
            assertThat(col.getPreferredWidth()).isEqualTo(80);

            // Switch back to English
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            frame.updateLanguage();

            assertThat(col.getHeaderValue()).isEqualTo("#");
            assertThat(col.getPreferredWidth()).isEqualTo(80);
        } finally {
            frame.dispose();
        }
    }

    // --- Test doubles for tracking resource lifecycle ---

    private static class TrackingAudioPlayer implements AudioPlayer {
        boolean closed = false;
        int stopCallCount = 0;
        private PlaybackState state = PlaybackState.STOPPED;
        private Track currentTrack = null;
        private final List<PlayerListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public void play(Track track) {
            this.currentTrack = track;
            this.state = PlaybackState.PLAYING;
            listeners.forEach(l -> l.onTrackStarted(track));
        }
        @Override public void pause() {
            this.state = PlaybackState.PAUSED;
            listeners.forEach(l -> l.onStateChanged(PlaybackState.PAUSED));
        }
        @Override public void resume() {
            this.state = PlaybackState.PLAYING;
            listeners.forEach(l -> l.onStateChanged(PlaybackState.PLAYING));
        }
        @Override public void stop() {
            this.state = PlaybackState.STOPPED;
            stopCallCount++;
            listeners.forEach(l -> l.onTrackFinished(currentTrack));
        }
        @Override public void seekTo(long seconds) {}
        @Override public void setVolume(float volume) {}
        @Override public float getVolume() { return 0.5f; }
        @Override public PlaybackState getState() { return state; }
        @Override public Track getCurrentTrack() { return currentTrack; }
        @Override public long getCurrentPositionSeconds() { return 0; }
        @Override public long getTotalDurationSeconds() { return 100; }
        @Override public boolean isPlaying() { return state == PlaybackState.PLAYING; }
        @Override public void addPlayerListener(PlayerListener listener) { listeners.add(listener); }
        @Override public void removePlayerListener(PlayerListener listener) { listeners.remove(listener); }
        @Override public void close() { closed = true; }
    }

    private static class TrackingPlayerController extends PlayerController {
        boolean disposedCalled = false;

        public TrackingPlayerController(AudioPlayer audioPlayer, ControlBarPanel controlBarPanel) {
            super(audioPlayer, controlBarPanel);
        }

        @Override
        public void dispose() {
            disposedCalled = true;
            super.dispose();
        }
    }
}
