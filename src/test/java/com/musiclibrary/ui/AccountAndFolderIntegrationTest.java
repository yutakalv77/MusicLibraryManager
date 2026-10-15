package com.musiclibrary.ui;

import com.musiclibrary.controller.PlayerController;
import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Account;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.PlaylistFolder;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.SqliteAccountRepository;
import com.musiclibrary.repository.SqlitePlaylistFolderRepository;
import com.musiclibrary.repository.SqlitePlaylistRepository;
import com.musiclibrary.repository.SqliteTrackRepository;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.ui.dialog.AccountManagerDialog;
import com.musiclibrary.ui.dialog.NewPlaylistFolderDialog;
import com.musiclibrary.ui.panel.ControlBarPanel;
import com.musiclibrary.ui.panel.SidebarPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccountAndFolderIntegrationTest {

    private DatabaseManager dbManager;
    private SqliteAccountRepository accountRepository;
    private SqlitePlaylistFolderRepository folderRepository;
    private SqlitePlaylistRepository playlistRepository;
    private SqliteTrackRepository trackRepository;

    @BeforeEach
    void setUp() throws SQLException {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);

        accountRepository = new SqliteAccountRepository(dbManager);
        folderRepository = new SqlitePlaylistFolderRepository(dbManager);
        playlistRepository = new SqlitePlaylistRepository(dbManager);
        trackRepository = new SqliteTrackRepository(dbManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("SidebarPanel displays playlists grouped under folders correctly")
    void testSidebarFolderDisplay() {
        SidebarPanel panel = new SidebarPanel();

        Playlist p1 = new Playlist("p1", "Workout Hits");
        p1.setFolderId("f1");

        Playlist p2 = new Playlist("p2", "Acoustic Solo");
        p2.setFolderId(null);

        Map<String, String> folderNames = Map.of("f1", "Gym");

        panel.setPlaylists(List.of(p1, p2), folderNames);

        assertThat(panel.getCurrentPlaylists()).containsExactly(p1, p2);
        assertThat(panel.getPlaylistList().getModel().getElementAt(0)).isEqualTo("📁  Gym / 🎵 Workout Hits");
        assertThat(panel.getPlaylistList().getModel().getElementAt(1)).isEqualTo("📁  Acoustic Solo");

        // Verify folder button and action callback
        AtomicBoolean folderActionCalled = new AtomicBoolean(false);
        panel.setOnNewPlaylistFolderAction(() -> folderActionCalled.set(true));

        panel.getNewFolderButton().doClick();
        assertThat(folderActionCalled.get()).isTrue();

        folderActionCalled.set(false);
        panel.getNewFolderMenuItem().doClick();
        assertThat(folderActionCalled.get()).isTrue();
    }

    @Test
    @DisplayName("NewPlaylistFolderDialog invokes callback when confirmed")
    void testNewPlaylistFolderDialogConfirm() throws Exception {
        AtomicReference<String> createdName = new AtomicReference<>();

        runOnEdt(() -> {
            NewPlaylistFolderDialog dialog = new NewPlaylistFolderDialog(null, createdName::set);
            dialog.getFolderNameField().setText("Favorites 2026");
            dialog.getOkButton().doClick();
        });

        assertThat(createdName.get()).isEqualTo("Favorites 2026");
    }

    @Test
    @DisplayName("AccountManagerDialog displays accounts and triggers selection callback")
    void testAccountManagerDialogSelection() throws Exception {
        Account userA = new Account("acc-1", "User Alpha");
        Account userB = new Account("acc-2", "User Beta");
        accountRepository.save(userA);
        accountRepository.save(userB);

        AtomicReference<Account> selectedAccount = new AtomicReference<>();

        runOnEdt(() -> {
            AccountManagerDialog dialog = new AccountManagerDialog(null, accountRepository, userA.getId(), selectedAccount::set);
            dialog.getAccountList().setSelectedValue(userB, true);
            dialog.getSwitchButton().doClick();
        });

        assertThat(selectedAccount.get()).isNotNull();
        assertThat(selectedAccount.get().getId()).isEqualTo("acc-2");
        assertThat(selectedAccount.get().getName()).isEqualTo("User Beta");
    }

    @Test
    @DisplayName("MainFrame switches account seamlessly, updating library, playlists, folders and window title")
    void testMainFrameAccountSwitchAndFolders() throws Exception {
        // Setup initial default account
        Account defaultAcc = new Account(Account.DEFAULT_ACCOUNT_ID, "Default User");
        accountRepository.save(defaultAcc);

        // Setup second account
        Account secondAcc = new Account("acc-user-2", "Jazz Fan");
        accountRepository.save(secondAcc);

        // Add a track & playlist to second account
        Track jazzTrack = Track.builder()
                .id("track-jazz-1")
                .filePath("C:/music/jazz.mp3")
                .title("Autumn Leaves")
                .artist("Bill Evans")
                .album("Portrait in Jazz")
                .durationSeconds(320)
                .fileSizeBytes(5000000)
                .build();
        trackRepository.importTrack(secondAcc.getId(), jazzTrack);

        PlaylistFolder jazzFolder = new PlaylistFolder.Builder()
                .id("f-jazz")
                .accountId(secondAcc.getId())
                .name("Jazz Collection")
                .build();
        folderRepository.save(jazzFolder);

        Playlist jazzPlaylist = new Playlist("pl-jazz", "Best of Evans");
        jazzPlaylist.setAccountId(secondAcc.getId());
        jazzPlaylist.setFolderId("f-jazz");
        jazzPlaylist.addTrack("track-jazz-1");
        playlistRepository.save(jazzPlaylist);

        Path tempStoragePath = Files.createTempFile("test_mainframe_account_", ".json");
        tempStoragePath.toFile().deleteOnExit();

        runOnEdt(() -> {
            MainFrame frame = new MainFrame(
                    new com.musiclibrary.model.Library(),
                    null,
                    null,
                    null,
                    null,
                    tempStoragePath,
                    dbManager
            );

            try {
                // Verify initial title with default account
                assertThat(frame.getTitle()).isEqualTo("Music Library Manager");
                assertThat(frame.getCurrentAccount().getId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);

                // Switch to second account
                frame.switchAccount(secondAcc);

                // Verify updated title and account
                assertThat(frame.getTitle()).isEqualTo("Music Library Manager - [Jazz Fan]");
                assertThat(frame.getCurrentAccount().getId()).isEqualTo("acc-user-2");
                assertThat(frame.getCurrentAccount().getName()).isEqualTo("Jazz Fan");

                // Verify playlists with folder display
                assertThat(frame.getSidebarPanel().getCurrentPlaylists()).hasSize(1);
                assertThat(frame.getSidebarPanel().getCurrentPlaylists().get(0).getName()).isEqualTo("Best of Evans");
                assertThat(frame.getSidebarPanel().getPlaylistList().getModel().getElementAt(0))
                        .isEqualTo("📁  Jazz Collection / 🎵 Best of Evans");

                // Switch back to default account
                frame.switchAccount(defaultAcc);
                assertThat(frame.getTitle()).isEqualTo("Music Library Manager");
                assertThat(frame.getCurrentAccount().getId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
                assertThat(frame.getSidebarPanel().getCurrentPlaylists()).isEmpty();

            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    @DisplayName("AccountManagerDialog strictly manages Switch and Delete button state transitions")
    void testAccountManagerDialogButtonStateTransitions() throws Exception {
        Account def = new Account(Account.DEFAULT_ACCOUNT_ID, "Default User");
        Account active = new Account("acc-active", "Active User");
        Account custom = new Account("acc-custom", "Custom User");
        accountRepository.save(def);
        accountRepository.save(active);
        accountRepository.save(custom);

        runOnEdt(() -> {
            AccountManagerDialog dialog = new AccountManagerDialog(null, accountRepository, active.getId(), null);

            // 1. Initial selection: Active account is auto-selected -> Switch & Delete disabled
            assertThat(dialog.getAccountList().getSelectedValue().getId()).isEqualTo("acc-active");
            assertThat(dialog.getSwitchButton().isEnabled()).isFalse();
            assertThat(dialog.getDeleteButton().isEnabled()).isFalse();

            // 2. Select custom non-active account -> Switch & Delete both enabled
            dialog.getAccountList().setSelectedValue(custom, true);
            assertThat(dialog.getSwitchButton().isEnabled()).isTrue();
            assertThat(dialog.getDeleteButton().isEnabled()).isTrue();

            // 3. Select default account (when not active) -> Switch enabled, but Delete MUST be disabled
            dialog.getAccountList().setSelectedValue(def, true);
            assertThat(dialog.getSwitchButton().isEnabled()).isTrue();
            assertThat(dialog.getDeleteButton().isEnabled()).isFalse();

            // 4. Clear selection -> Switch & Delete both disabled
            dialog.getAccountList().clearSelection();
            assertThat(dialog.getSwitchButton().isEnabled()).isFalse();
            assertThat(dialog.getDeleteButton().isEnabled()).isFalse();

            dialog.dispose();
        });
    }

    @Test
    @DisplayName("NewPlaylistFolderDialog ignores empty or blank names and trims valid input")
    void testNewPlaylistFolderDialogValidationAndTrimming() throws Exception {
        AtomicReference<String> created = new AtomicReference<>();

        runOnEdt(() -> {
            NewPlaylistFolderDialog dialog = new NewPlaylistFolderDialog(null, created::set);

            // 1. Empty input -> OK does not invoke callback
            dialog.getFolderNameField().setText("");
            dialog.getOkButton().doClick();
            assertThat(created.get()).isNull();

            // 2. Blank whitespace input -> OK does not invoke callback
            dialog.getFolderNameField().setText("    ");
            dialog.getOkButton().doClick();
            assertThat(created.get()).isNull();

            // 3. Cancel button -> closes without invoking callback
            dialog.getCancelButton().doClick();
            assertThat(created.get()).isNull();
            assertThat(dialog.isDisplayable()).isFalse();

            // 4. Valid with surrounding whitespace -> trimmed and invoked
            NewPlaylistFolderDialog dialog2 = new NewPlaylistFolderDialog(null, created::set);
            dialog2.getFolderNameField().setText("   Study Beats   ");
            dialog2.getOkButton().doClick();
            assertThat(created.get()).isEqualTo("Study Beats");
            assertThat(dialog2.isDisplayable()).isFalse();
        });
    }

    @Test
    @DisplayName("SidebarPanel displays multiple folders and root playlists with strict order preservation")
    void testSidebarMultipleFoldersAndPlaylistsHierarchy() {
        SidebarPanel panel = new SidebarPanel();

        Playlist p1 = new Playlist("p1", "Lo-Fi 1");
        p1.setFolderId("f1");

        Playlist p2 = new Playlist("p2", "Lo-Fi 2");
        p2.setFolderId("f1");

        Playlist p3 = new Playlist("p3", "Party Anthem");
        p3.setFolderId("f2");

        Playlist p4 = new Playlist("p4", "Root Playlist");
        p4.setFolderId(null);

        Playlist p5 = new Playlist("p5", "Unknown Folder Playlist");
        p5.setFolderId("f-missing"); // folderId not in map

        Map<String, String> folderNames = Map.of(
                "f1", "Study Zone",
                "f2", "Celebration"
        );

        panel.setPlaylists(List.of(p1, p2, p3, p4, p5), folderNames);

        assertThat(panel.getCurrentPlaylists()).containsExactly(p1, p2, p3, p4, p5);
        assertThat(panel.getPlaylistList().getModel().getSize()).isEqualTo(5);

        assertThat(panel.getPlaylistList().getModel().getElementAt(0)).isEqualTo("📁  Study Zone / 🎵 Lo-Fi 1");
        assertThat(panel.getPlaylistList().getModel().getElementAt(1)).isEqualTo("📁  Study Zone / 🎵 Lo-Fi 2");
        assertThat(panel.getPlaylistList().getModel().getElementAt(2)).isEqualTo("📁  Celebration / 🎵 Party Anthem");
        assertThat(panel.getPlaylistList().getModel().getElementAt(3)).isEqualTo("📁  Root Playlist");
        assertThat(panel.getPlaylistList().getModel().getElementAt(4)).isEqualTo("📁  Unknown Folder Playlist");
    }

    @Test
    @DisplayName("MainFrame account switch immediately stops active playback and cleans state")
    void testMainFrameAccountSwitchPlaybackStoppage() throws Exception {
        Account userA = new Account("acc-a", "User A");
        Account userB = new Account("acc-b", "User B");
        accountRepository.save(userA);
        accountRepository.save(userB);

        TestAudioPlayer audioPlayer = new TestAudioPlayer();
        ControlBarPanel controlBar = new ControlBarPanel();
        PlayerController playerController = new PlayerController(audioPlayer, controlBar);

        Track song = Track.builder().id("s-1").filePath("C:/music/song.mp3").title("Playing Song").build();

        Path tempStoragePath = Files.createTempFile("test_mainframe_audio_", ".json");
        tempStoragePath.toFile().deleteOnExit();

        runOnEdt(() -> {
            MainFrame frame = new MainFrame(
                    new com.musiclibrary.model.Library(),
                    null,
                    audioPlayer,
                    playerController,
                    null,
                    tempStoragePath,
                    dbManager
            );

            try {
                // Start playback in current account
                playerController.play(song);
                assertThat(audioPlayer.isPlaying()).isTrue();

                // Switch account -> Active playback MUST stop immediately
                frame.switchAccount(userB);
                assertThat(audioPlayer.isPlaying()).isFalse();

            } finally {
                frame.dispose();
            }
        });
    }

    @Test
    @DisplayName("All Account and Folder i18n keys resolve properly without missing tokens in English and Japanese")
    void testI18nAccountAndFolderTranslations() {
        List<String> keys = List.of(
                "menu.account",
                "menu.account.switch",
                "menu.account.manage",
                "dialog.account.title",
                "dialog.account.new",
                "dialog.account.name",
                "dialog.account.switch_btn",
                "dialog.account.delete_btn",
                "dialog.account.delete_confirm",
                "dialog.folder.title",
                "dialog.folder.name",
                "sidebar.new_folder",
                "dialog.account.active_suffix",
                "dialog.account.no_db"
        );

        // 1. English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        for (String key : keys) {
            String text = I18nManager.getText(key);
            assertThat(text).isNotBlank();
            assertThat(text).doesNotStartWith("!");
            assertThat(text).doesNotEndWith("!");
        }

        // 2. Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        for (String key : keys) {
            String text = I18nManager.getText(key);
            assertThat(text).isNotBlank();
            assertThat(text).doesNotStartWith("!");
            assertThat(text).doesNotEndWith("!");
        }

        // Restore
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("AccountManagerDialog cell renderer displays active suffix for current account")
    void testAccountManagerDialogCellRenderer() throws Exception {
        Account def = new Account(Account.DEFAULT_ACCOUNT_ID, "Default User");
        Account active = new Account("acc-active", "Active User");
        accountRepository.save(def);
        accountRepository.save(active);

        runOnEdt(() -> {
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            AccountManagerDialog dialog = new AccountManagerDialog(null, accountRepository, active.getId(), null);
            JList<Account> list = dialog.getAccountList();
            ListCellRenderer<? super Account> renderer = list.getCellRenderer();

            // Render active item in English
            Component activeComp = renderer.getListCellRendererComponent(list, active, 1, false, false);
            assertThat(activeComp).isInstanceOf(JLabel.class);
            assertThat(((JLabel) activeComp).getText()).isEqualTo("Active User (Active)");

            // Render non-active item in English
            Component defComp = renderer.getListCellRendererComponent(list, def, 0, false, false);
            assertThat(defComp).isInstanceOf(JLabel.class);
            assertThat(((JLabel) defComp).getText()).isEqualTo("Default User");

            // Test Japanese locale
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            Component activeCompJa = renderer.getListCellRendererComponent(list, active, 1, false, false);
            assertThat(((JLabel) activeCompJa).getText()).isEqualTo("Active User (現在のアカウント)");

            // Restore
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

            dialog.dispose();
        });
    }


    private static void runOnEdt(Runnable runnable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }
    }

    private static class TestAudioPlayer implements AudioPlayer {
        private PlaybackState state = PlaybackState.STOPPED;
        private Track currentTrack;
        private float volume = 0.7f;
        private final List<PlayerListener> listeners = new ArrayList<>();

        @Override
        public void play(Track track) {
            this.currentTrack = track;
            this.state = PlaybackState.PLAYING;
            listeners.forEach(l -> {
                l.onStateChanged(PlaybackState.PLAYING);
                l.onTrackStarted(track);
            });
        }

        @Override
        public void pause() {
            this.state = PlaybackState.PAUSED;
            listeners.forEach(l -> l.onStateChanged(PlaybackState.PAUSED));
        }

        @Override
        public void resume() {
            this.state = PlaybackState.PLAYING;
            listeners.forEach(l -> l.onStateChanged(PlaybackState.PLAYING));
        }

        @Override
        public void stop() {
            this.state = PlaybackState.STOPPED;
            listeners.forEach(l -> l.onStateChanged(PlaybackState.STOPPED));
        }

        @Override
        public void seekTo(long seconds) {}

        @Override
        public void setVolume(float volume) {
            this.volume = volume;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public PlaybackState getState() {
            return state;
        }

        @Override
        public Track getCurrentTrack() {
            return currentTrack;
        }

        @Override
        public long getCurrentPositionSeconds() {
            return 0;
        }

        @Override
        public long getTotalDurationSeconds() {
            return currentTrack != null ? currentTrack.getDurationSeconds() : 0;
        }

        @Override
        public boolean isPlaying() {
            return state == PlaybackState.PLAYING;
        }

        @Override
        public void addPlayerListener(PlayerListener listener) {
            if (listener != null) listeners.add(listener);
        }

        @Override
        public void removePlayerListener(PlayerListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void close() {
            stop();
        }
    }
}
