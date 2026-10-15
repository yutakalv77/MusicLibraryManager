package com.musiclibrary.ui;

import com.musiclibrary.Main;
import com.musiclibrary.controller.LibraryController;
import com.musiclibrary.controller.PlayerController;
import com.musiclibrary.controller.QueueChangeListener;
import com.musiclibrary.controller.SearchController;
import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.i18n.LanguageChangeListener;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.BasicAudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.service.importer.FolderWatcherService;
import com.musiclibrary.service.importer.ImportProgressListener;
import com.musiclibrary.service.importer.ImportResult;
import com.musiclibrary.service.importer.LibraryImportService;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import com.musiclibrary.service.storage.MissingTrackResolver;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeChangeListener;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.dialog.ImportProgressDialog;
import com.musiclibrary.ui.dialog.TagEditorDialog;
import com.musiclibrary.ui.dnd.TrackDropTargetListener;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Account;
import com.musiclibrary.model.PlaylistFolder;
import com.musiclibrary.repository.AccountRepository;
import com.musiclibrary.repository.PlaylistFolderRepository;
import com.musiclibrary.repository.SqliteAccountRepository;
import com.musiclibrary.repository.SqlitePlaylistFolderRepository;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.service.storage.LibraryStorage;
import com.musiclibrary.service.storage.SqliteLibraryStorage;
import com.musiclibrary.settings.SettingsManager;
import com.musiclibrary.ui.dialog.AccountManagerDialog;
import com.musiclibrary.ui.dialog.NewPlaylistFolderDialog;
import com.musiclibrary.ui.panel.AlbumGridViewPanel;
import com.musiclibrary.ui.panel.AlbumHeaderPanel;
import com.musiclibrary.ui.menu.AppMenuBar;
import com.musiclibrary.ui.panel.ControlBarPanel;
import com.musiclibrary.ui.panel.SidebarPanel;
import com.musiclibrary.ui.panel.StatusBarPanel;
import com.musiclibrary.ui.panel.TrackTablePanel;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Main application window coordinating top control bar, left sidebar,
 * center track table, and bottom status bar in a 3-pane responsive layout.
 */
public class MainFrame extends JFrame {

    private static final System.Logger LOGGER = System.getLogger(MainFrame.class.getName());

    // Domain & Services
    private final Library library;
    private final LibraryImportService importService;
    private final FolderWatcherService folderWatcherService;
    private final AudioPlayer audioPlayer;
    private final PlayerController playerController;
    private final FastSearchIndex searchIndex;
    private final SearchController searchController;
    private final LibraryController libraryController;
    private final MiniPlayerFrame miniPlayerFrame;

    // Main panels
    private final ControlBarPanel controlBarPanel;
    private final SidebarPanel sidebarPanel;
    private final TrackTablePanel trackTablePanel;
    private final AlbumGridViewPanel albumGridViewPanel;
    private final AlbumHeaderPanel albumHeaderPanel;
    private final JPanel mainContentPanel;
    private final CardLayout cardLayout;
    private final StatusBarPanel statusBarPanel;
    private final JSplitPane splitPane;

    public static final String CARD_TABLE = "TABLE";
    public static final String CARD_ALBUMS = "ALBUMS";
    private boolean isAlbumGridActive = false;
    private volatile List<Album> cachedAlbums = null;

    // Menu component
    private final AppMenuBar appMenuBar;

    // Storage, Account & DB
    private final Path storagePath;
    private DatabaseManager dbManager;
    private Account currentAccount;
    private SqliteAccountRepository accountRepository;
    private SqlitePlaylistFolderRepository folderRepository;
    private LibraryStorage storage;

    private final LanguageChangeListener languageListener;
    private final ThemeChangeListener themeListener;
    private final KeyboardShortcutManager shortcutManager;

    public MainFrame() {
        this(new Library(), null, null, null, null, null, null);
    }

    public MainFrame(Library library, LibraryImportService importService) {
        this(library, importService, null, null, null, null, null);
    }

    public MainFrame(Library library, LibraryImportService importService, Path storagePath) {
        this(library, importService, null, null, null, storagePath, null);
    }

    public MainFrame(Library library, LibraryImportService importService, AudioPlayer customAudioPlayer, PlayerController customPlayerController) {
        this(library, importService, customAudioPlayer, customPlayerController, null, null, null);
    }

    public MainFrame(Library library, LibraryImportService importService, AudioPlayer customAudioPlayer, PlayerController customPlayerController, LibraryController customLibraryController) {
        this(library, importService, customAudioPlayer, customPlayerController, customLibraryController, null, null);
    }

    public MainFrame(Library library, LibraryImportService importService, AudioPlayer customAudioPlayer, PlayerController customPlayerController, LibraryController customLibraryController, Path storagePath) {
        this(library, importService, customAudioPlayer, customPlayerController, customLibraryController, storagePath, null);
    }

    public MainFrame(Library library, LibraryImportService importService, AudioPlayer customAudioPlayer, PlayerController customPlayerController, LibraryController customLibraryController, Path storagePath, DatabaseManager dbManager) {
        Path effectiveStoragePath;
        if (storagePath != null) {
            effectiveStoragePath = storagePath;
        } else {
            String prop = System.getProperty(Main.STORAGE_PATH_PROPERTY);
            if (prop != null && !prop.isBlank()) {
                effectiveStoragePath = Paths.get(prop);
            } else {
                try {
                    effectiveStoragePath = Files.createTempFile("musiclibrary_isolated_", ".json");
                    effectiveStoragePath.toFile().deleteOnExit();
                } catch (IOException e) {
                    effectiveStoragePath = Main.DEFAULT_LIBRARY_PATH;
                }
            }
        }

        this.storagePath = effectiveStoragePath;
        this.dbManager = dbManager;
        this.currentAccount = new Account(Account.DEFAULT_ACCOUNT_ID, "Default User");

        if (this.dbManager != null) {
            try {
                this.accountRepository = new SqliteAccountRepository(this.dbManager);
                this.folderRepository = new SqlitePlaylistFolderRepository(this.dbManager);
                var existing = this.accountRepository.findById(Account.DEFAULT_ACCOUNT_ID);
                if (existing.isPresent()) {
                    this.currentAccount = existing.get();
                } else {
                    this.accountRepository.save(this.currentAccount);
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to initialize DB repositories in MainFrame: " + e.getMessage(), e);
            }
        }

        this.storage = (this.dbManager != null)
                ? new SqliteLibraryStorage(this.dbManager, this.currentAccount.getId())
                : new JsonLibraryStorage();

        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.importService = (importService != null) ? importService : new LibraryImportService(this.library, effectiveStoragePath);
        this.folderWatcherService = new FolderWatcherService(this.importService);
        this.audioPlayer = (customAudioPlayer != null) ? customAudioPlayer : new BasicAudioPlayer();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(800, 520));
        setLocationRelativeTo(null);

        // 1. Initialize Panels
        controlBarPanel = new ControlBarPanel();
        sidebarPanel = new SidebarPanel();
        trackTablePanel = new TrackTablePanel();
        statusBarPanel = new StatusBarPanel();

        albumGridViewPanel = new AlbumGridViewPanel();
        albumHeaderPanel = new AlbumHeaderPanel();
        albumHeaderPanel.setVisible(false);

        albumGridViewPanel.setOnAlbumSelectedAction(this::openAlbumDetail);
        albumHeaderPanel.setOnBackAction(this::returnFromAlbumDetail);

        cardLayout = new CardLayout();
        mainContentPanel = new JPanel(cardLayout);

        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.add(albumHeaderPanel, BorderLayout.NORTH);
        tableContainer.add(trackTablePanel, BorderLayout.CENTER);

        mainContentPanel.add(tableContainer, CARD_TABLE);
        mainContentPanel.add(albumGridViewPanel, CARD_ALBUMS);

        // 2. Wire Controller & Playback Actions
        this.playerController = (customPlayerController != null)
                ? customPlayerController
                : new PlayerController(this.audioPlayer, this.controlBarPanel);
        this.trackTablePanel.setOnPlayAction(this::playTrackSafely);
        this.trackTablePanel.setOnPlayNextAction(this::enqueueNextSafely);
        this.trackTablePanel.setOnEditTagsAction(this::openTagEditorForTracks);

        this.audioPlayer.addPlayerListener(new PlayerListener() {
            @Override
            public void onTrackStarted(Track track) {
                trackTablePanel.setPlaybackStatus(track != null ? track.getId() : null, PlaybackState.PLAYING);
            }

            @Override
            public void onStateChanged(PlaybackState newState) {
                Track cur = audioPlayer.getCurrentTrack();
                trackTablePanel.setPlaybackStatus(cur != null ? cur.getId() : null, newState);
            }

            @Override
            public void onTrackFinished(Track track) {
                trackTablePanel.setPlaybackStatus(null, PlaybackState.STOPPED);
            }

            @Override
            public void onError(Throwable error) {
                trackTablePanel.setPlaybackStatus(null, PlaybackState.STOPPED);
            }
        });

        // 3. Setup Fast Search Index & Search Controller
        this.searchIndex = new FastSearchIndex();
        this.searchIndex.indexTracks(this.library.getTracks());
        this.searchController = new SearchController(
                this.searchIndex,
                this.controlBarPanel.getSearchField(),
                this.trackTablePanel
        );
        this.searchController.addMatchCountListener(count -> updateStatusBarSummary());
        this.searchController.addQueryListener(query -> {
            if (isAlbumGridActive) {
                albumGridViewPanel.filterAlbums(query);
                updateStatusBarSummary();
            }
        });

        // 4. Setup Library Controller & Playlist UI Actions
        this.libraryController = (customLibraryController != null)
                ? customLibraryController
                : new LibraryController(
                        this.library,
                        this.sidebarPanel,
                        this.trackTablePanel,
                        this.storage,
                        effectiveStoragePath,
                        this.searchIndex
                );
        this.libraryController.addLibraryContentChangeListener(this::invalidateAlbumCache);

        this.libraryController.addNavigationListener(new LibraryController.ViewNavigationListener() {
            @Override
            public void onSectionChanged(SidebarPanel.LibrarySection section) {
                if (section == SidebarPanel.LibrarySection.ALBUMS) {
                    showAlbumGrid();
                } else {
                    isAlbumGridActive = false;
                    albumHeaderPanel.setVisible(false);
                    cardLayout.show(mainContentPanel, CARD_TABLE);
                }
                updateStatusBarSummary();
            }

            @Override
            public void onPlaylistChanged(String playlistId) {
                isAlbumGridActive = false;
                albumHeaderPanel.setVisible(false);
                cardLayout.show(mainContentPanel, CARD_TABLE);
                updateStatusBarSummary();
            }
        });

        this.sidebarPanel.setOnNewPlaylistAction(() -> {
            String name = JOptionPane.showInputDialog(this,
                    I18nManager.getInstance().get("sidebar.playlist.new_prompt"),
                    I18nManager.getInstance().get("sidebar.new_playlist"),
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null) {
                this.libraryController.createPlaylist(name);
            }
        });

        this.sidebarPanel.setOnPlaylistRenameAction(p -> {
            String newName = JOptionPane.showInputDialog(this,
                    I18nManager.getInstance().get("sidebar.playlist.rename_prompt"),
                    p.getName());
            if (newName != null && !newName.isBlank()) {
                this.libraryController.renamePlaylist(p.getId(), newName);
            }
        });

        this.sidebarPanel.setOnPlaylistDeleteAction(p -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    I18nManager.getInstance().format("sidebar.playlist.confirm_delete", p.getName()),
                    I18nManager.getInstance().get("sidebar.playlist.delete"),
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                this.libraryController.deletePlaylist(p.getId());
            }
        });

        this.sidebarPanel.setOnNewPlaylistFolderAction(this::showNewPlaylistFolderDialog);

        this.libraryController.setOnPlayTracksAction(tracks -> {
            if (!tracks.isEmpty()) {
                this.playerController.play(tracks.get(0), tracks);
            }
        });

        // 5. Setup Mini Player Frame
        this.miniPlayerFrame = new MiniPlayerFrame(this.playerController, this::restoreFromMiniPlayer);
        this.controlBarPanel.getMiniPlayerButton().addActionListener(e -> switchToMiniPlayer());

        // 6. Setup Split Pane (Sidebar + Main Content)
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, mainContentPanel);
        splitPane.setDividerLocation(220);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        // 7. Assemble Window Layout
        setLayout(new BorderLayout());
        add(controlBarPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusBarPanel, BorderLayout.SOUTH);

        // 8. Initialize Menu Bar
        appMenuBar = new AppMenuBar();
        appMenuBar.setOnAddFileAction(this::chooseFileAndImport);
        appMenuBar.setOnAddFolderAction(this::chooseFolderAndImport);
        appMenuBar.setOnRescanLibraryAction(this::rescanLibrary);
        appMenuBar.setOnRescanMissingAction(this::rescanAndRelinkMissing);
        appMenuBar.setOnExitAction(() -> {
            if (audioPlayer != null) {
                audioPlayer.stop();
            }
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });
        appMenuBar.setOnEditTagsAction(this::openTagEditorForSelection);
        appMenuBar.setOnMiniPlayerAction(this::switchToMiniPlayer);
        appMenuBar.setOnPlayAction(() -> {
            Track selected = trackTablePanel.getSelectedTrack();
            if (selected != null && playerController.getAudioPlayer().getState() == PlaybackState.STOPPED) {
                playTrackSafely(selected);
            } else {
                playerController.play();
            }
        });
        appMenuBar.setOnPauseAction(playerController::pause);
        appMenuBar.setOnNextAction(playerController::next);
        appMenuBar.setOnPrevAction(playerController::previous);
        appMenuBar.setOnShuffleAction(playerController::setShuffle);
        appMenuBar.setOnRepeatModeAction(playerController::setRepeatMode);
        appMenuBar.setOnSwitchAccountAction(this::showAccountManagerDialog);

        setJMenuBar(appMenuBar);

        // 9. Register Listeners for i18n, Theme & Queue
        this.playerController.addQueueListener(new QueueChangeListener() {
            @Override
            public void onRepeatModeChanged(RepeatMode mode) {
                updateRepeatMenuSelection(mode);
            }

            @Override
            public void onShuffleChanged(boolean shuffle) {
                appMenuBar.setShuffleSelected(shuffle);
            }
        });
        updateRepeatMenuSelection(this.playerController.getRepeatMode());
        appMenuBar.setShuffleSelected(this.playerController.isShuffle());

        languageListener = newLocale -> updateLanguage();
        I18nManager.getInstance().addLanguageChangeListener(languageListener);

        themeListener = newTheme -> updateThemeSelection();
        ThemeManager.getInstance().addThemeChangeListener(themeListener);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (audioPlayer != null) {
                    try {
                        audioPlayer.stop();
                    } catch (Exception ex) {
                        LOGGER.log(System.Logger.Level.WARNING, "Error stopping audio on window closing: " + ex.getMessage(), ex);
                    }
                }
            }
        });

        // 6. Global Keyboard Shortcuts
        shortcutManager = new KeyboardShortcutManager(getRootPane());
        setupKeyboardShortcuts();

        // 7. Drag & Drop Integration
        setupDragAndDrop();

        updateLanguage();
        updateThemeSelection();
        updateLanguageSelection();
        refreshPlaylistsWithFolders();
        refreshLibraryView();
    }

    private void setupKeyboardShortcuts() {
        shortcutManager.setOnSearchFocus(() -> {
            var searchField = controlBarPanel.getSearchField();
            searchField.requestFocusInWindow();
            searchField.selectAll();
        });

        shortcutManager.setOnThemeToggle(() -> {
            ThemeManager.getInstance().toggleTheme();
        });

        shortcutManager.setOnVolumeUp(() -> {
            var slider = controlBarPanel.getVolumeSlider();
            playerController.setVolumePercent(slider.getValue() + 5);
        });

        shortcutManager.setOnVolumeDown(() -> {
            var slider = controlBarPanel.getVolumeSlider();
            playerController.setVolumePercent(slider.getValue() - 5);
        });

        shortcutManager.setOnPlayPauseToggle(() -> {
            Track selected = trackTablePanel.getSelectedTrack();
            if (playerController.getAudioPlayer().getState() == PlaybackState.STOPPED && selected != null) {
                playerController.play(selected);
            } else {
                playerController.togglePlayPause();
            }
        });

        shortcutManager.setOnSkipForward(() -> {
            playerController.skipForward();
        });

        shortcutManager.setOnSkipBackward(() -> {
            playerController.skipBackward();
        });

        shortcutManager.setOnMiniPlayerToggle(this::switchToMiniPlayer);

        shortcutManager.setOnAddFiles(() -> {
            appMenuBar.getAddFileItem().doClick();
        });

        shortcutManager.setOnEditTags(this::openTagEditorForSelection);
    }

    /**
     * Hides the main frame and opens the compact mini player mode,
     * maintaining uninterrupted playback and synchronized state.
     */
    public void switchToMiniPlayer() {
        setVisible(false);
        if (miniPlayerFrame != null) {
            miniPlayerFrame.syncState();
            miniPlayerFrame.setLocationRelativeTo(this);
            miniPlayerFrame.setVisible(true);
            miniPlayerFrame.toFront();
        }
    }

    /**
     * Hides the mini player and restores the main frame into the foreground.
     */
    public void restoreFromMiniPlayer() {
        if (miniPlayerFrame != null) {
            miniPlayerFrame.setVisible(false);
        }
        setVisible(true);
        toFront();
        refreshLibraryView();
    }

    /**
     * Plays track safely, checking if the backing file is missing beforehand.
     */
    public void playTrackSafely(Track track) {
        if (track == null) {
            return;
        }
        if (MissingTrackResolver.isMissing(track)) {
            track.setMissing(true);
            trackTablePanel.updateMissingTracks(List.of(track));
            if (isShowing()) {
                JOptionPane.showMessageDialog(this,
                        I18nManager.getInstance().get("missing.play_error"),
                        I18nManager.getInstance().get("missing.dialog.title"),
                        JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        List<Track> allTracks = this.trackTablePanel.getTableModel().getTracks();
        this.playerController.play(track, allTracks);
    }

    /**
     * Enqueues next track safely, checking if the backing file is missing beforehand.
     */
    public void enqueueNextSafely(Track track) {
        if (track == null) {
            return;
        }
        if (MissingTrackResolver.isMissing(track)) {
            track.setMissing(true);
            trackTablePanel.updateMissingTracks(List.of(track));
        }
        this.playerController.enqueueNext(track);
    }

    /**
     * Scans for missing tracks, prompts user for search directory, and runs background resolution.
     */
    public void rescanAndRelinkMissing() {
        List<Track> missing = libraryController.getMissingTracks();
        trackTablePanel.updateMissingTracks(missing);

        I18nManager i18n = I18nManager.getInstance();
        if (missing.isEmpty()) {
            if (isShowing()) {
                JOptionPane.showMessageDialog(this,
                        i18n.get("missing.dialog.no_missing"),
                        i18n.get("missing.dialog.title"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(i18n.get("missing.dialog.title"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        Path targetDir = chooser.getSelectedFile().toPath();
        statusBarPanel.setStatus(i18n.format("status.scanning", targetDir.getFileName()));
        statusBarPanel.showProgress(true, 0, 100);

        libraryController.resolveMissingTracksAsync(targetDir, new MissingTrackResolver.ResolveProgressListener() {
            @Override
            public void onProgress(int current, int total, Path currentPath) {
                statusBarPanel.showProgress(false, current, total);
                if (currentPath != null) {
                    statusBarPanel.setStatus(i18n.format("status.scanning", currentPath.getFileName()));
                }
            }

            @Override
            public void onRelinked(Track track, Path newPath) {
                // Relinked state handled by controller
            }

            @Override
            public void onComplete(int relinkedCount, int remainingCount) {
                statusBarPanel.hideProgress();
                statusBarPanel.setStatus(i18n.get("status.ready"));
                trackTablePanel.updateMissingTracks(libraryController.getMissingTracks());
                refreshLibraryView();

                if (relinkedCount > 0) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            i18n.format("missing.dialog.relinked", relinkedCount, remainingCount),
                            i18n.get("missing.dialog.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            i18n.get("missing.dialog.none_found"),
                            i18n.get("missing.dialog.title"),
                            JOptionPane.WARNING_MESSAGE);
                }
            }

            @Override
            public void onError(Throwable error) {
                statusBarPanel.hideProgress();
                statusBarPanel.setStatus(i18n.format("common.error", error.getMessage()));
            }
        });
    }

    /**
     * Opens the ID3 Tag Editor dialog for the currently selected track(s) in the table.
     */
    public void openTagEditorForSelection() {
        List<Track> selected = trackTablePanel.getSelectedTracks();
        if (selected.isEmpty()) {
            return;
        }
        openTagEditorForTracks(selected);
    }

    /**
     * Opens the ID3 Tag Editor dialog for the specified track(s).
     *
     * @param tracks tracks to edit
     */
    public void openTagEditorForTracks(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        I18nManager i18n = I18nManager.getInstance();
        TagEditorDialog dialog = new TagEditorDialog(this, tracks, update -> {
            statusBarPanel.setStatus(i18n.get("tageditor.status.updating"));
            libraryController.updateTracksMetadata(
                    tracks,
                    update,
                    () -> {
                        statusBarPanel.setStatus(i18n.format("tageditor.status.updated", tracks.size()));
                        updateStatusBarSummary();
                    },
                    error -> {
                        statusBarPanel.setStatus(i18n.format("tageditor.status.error", error.getMessage()));
                    }
            );
        });
        dialog.setVisible(true);
    }

    /**
     * Opens folder picker dialog and begins importing selected folder.
     */
    public void chooseFolderAndImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18nManager.getInstance().get("menu.file.add_folder"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            importFolder(chooser.getSelectedFile().toPath());
        }
    }

    /**
     * Opens file picker dialog and begins importing selected audio files.
     */
    public void chooseFileAndImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle(I18nManager.getInstance().get("menu.file.add_file"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFiles() != null) {
            for (File file : chooser.getSelectedFiles()) {
                importFolder(file.toPath());
            }
        }
    }

    /**
     * Opens folder picker dialog and begins full rescan and metadata update for library.
     */
    public void rescanLibrary() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(I18nManager.getInstance().get("dialog.rescan.title"));

        Path defaultPath = getDefaultMusicFolderPath();
        if (defaultPath != null && Files.isDirectory(defaultPath)) {
            chooser.setSelectedFile(defaultPath.toFile());
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            rescanFolder(chooser.getSelectedFile().toPath());
        }
    }

    private Path getDefaultMusicFolderPath() {
        for (Track t : library.getTracks()) {
            if (t.getFilePath() != null && !t.getFilePath().isBlank()) {
                try {
                    Path p = Paths.get(t.getFilePath()).getParent();
                    if (p != null && Files.isDirectory(p)) {
                        return p;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Launches asynchronous folder rescan with forced metadata update, progress dialog,
     * and status bar coordination.
     *
     * @param folderPath path to folder to rescan
     */
    public void rescanFolder(Path folderPath) {
        if (folderPath == null) {
            return;
        }

        ImportProgressDialog dialog = new ImportProgressDialog(this);

        var task = importService.rescanAndImportAsync(folderPath, new ImportProgressListener() {
            @Override
            public void onProgress(int processedCount, int totalCount, String currentFileName) {
                dialog.updateProgress(processedCount, totalCount, currentFileName);
                if (currentFileName != null && !currentFileName.isBlank()) {
                    statusBarPanel.setStatus(I18nManager.getInstance().format("status.scanning", currentFileName));
                }
                if (totalCount > 0) {
                    statusBarPanel.showProgress(false, processedCount, totalCount);
                }
            }

            @Override
            public void onBatchImported(List<Track> importedBatch) {
                trackTablePanel.addTracks(importedBatch);
                if (searchIndex != null) {
                    searchIndex.indexTracks(importedBatch);
                }
                if (searchController != null && searchController.hasActiveFilter()) {
                    searchController.applyFilter();
                }
                updateStatusBarSummary();
            }

            @Override
            public void onCompleted(ImportResult result) {
                dialog.dispose();
                statusBarPanel.hideProgress();
                refreshLibraryView();
                if (result != null) {
                    statusBarPanel.setStatus(I18nManager.getInstance().format(
                            "toast.rescan_detail",
                            result.updatedCount(),
                            result.addedCount()
                    ));
                } else {
                    statusBarPanel.setStatus(I18nManager.getInstance().get("status.ready"));
                }
            }

            @Override
            public void onCompleted(int totalImportedCount) {
                onCompleted(new ImportResult(0, totalImportedCount, 0));
            }

            @Override
            public void onCancelled(int partialImportedCount) {
                dialog.dispose();
                statusBarPanel.hideProgress();
                refreshLibraryView();
                statusBarPanel.setStatus(I18nManager.getInstance().get("status.ready"));
            }

            @Override
            public void onError(Throwable error) {
                dialog.dispose();
                statusBarPanel.hideProgress();
                statusBarPanel.setStatus(I18nManager.getInstance().format("common.error", error.getMessage()));
            }
        });

        dialog.setOnCancel(task::cancel);
        dialog.setOnBackground(() -> statusBarPanel.showProgress(true, 0, 100));

        dialog.setVisible(true);
    }

    private void setupDragAndDrop() {
        // Drop on Track Table: import to library
        new TrackDropTargetListener(trackTablePanel, (paths, dropPoint) -> {
            importPaths(paths, null);
        });

        // Drop on Playlist List: import to library AND add to the specific playlist under cursor,
        // or add dragged tracks from track table to the playlist
        new TrackDropTargetListener(sidebarPanel.getPlaylistList(),
                (paths, dropPoint) -> {
                    int index = sidebarPanel.getPlaylistList().locationToIndex(dropPoint);
                    List<Playlist> playlists = sidebarPanel.getCurrentPlaylists();
                    Playlist targetPlaylist = (index >= 0 && index < playlists.size()) ? playlists.get(index) : null;
                    importPaths(paths, targetPlaylist);
                },
                (trackIds, dropPoint) -> {
                    int index = sidebarPanel.getPlaylistList().locationToIndex(dropPoint);
                    List<Playlist> playlists = sidebarPanel.getCurrentPlaylists();
                    if (index >= 0 && index < playlists.size()) {
                        libraryController.addTracksToPlaylist(playlists.get(index).getId(), trackIds);
                    }
                }
        );
    }

    /**
     * Launches asynchronous folder import with progress dialog and status bar coordination.
     *
     * @param folderPath path to folder or file to import
     */
    public void importFolder(Path folderPath) {
        if (folderPath != null) {
            importPaths(List.of(folderPath), null);
        }
    }

    /**
     * Launches asynchronous import for multiple paths with optional playlist assignment.
     *
     * @param paths          collection of audio files or folders
     * @param targetPlaylist optional playlist to receive imported tracks
     */
    public void importPaths(Collection<Path> paths, Playlist targetPlaylist) {
        if (paths == null || paths.isEmpty()) {
            return;
        }

        ImportProgressDialog dialog = new ImportProgressDialog(this);

        var task = importService.scanAndImportAsync(paths, new ImportProgressListener() {
            @Override
            public void onProgress(int processedCount, int totalCount, String currentFileName) {
                dialog.updateProgress(processedCount, totalCount, currentFileName);
                if (currentFileName != null && !currentFileName.isBlank()) {
                    statusBarPanel.setStatus(I18nManager.getInstance().format("status.scanning", currentFileName));
                }
                if (totalCount > 0) {
                    statusBarPanel.showProgress(false, processedCount, totalCount);
                }
            }

            @Override
            public void onBatchImported(List<Track> importedBatch) {
                if (targetPlaylist != null) {
                    for (Track track : importedBatch) {
                        targetPlaylist.addTrack(track.getId());
                    }
                }
                // High-performance batch addition directly into table model without full reload
                trackTablePanel.addTracks(importedBatch);
                if (searchIndex != null) {
                    searchIndex.indexTracks(importedBatch);
                }
                if (searchController != null && searchController.hasActiveFilter()) {
                    searchController.applyFilter();
                }
                updateStatusBarSummary();
            }

            @Override
            public void onCompleted(ImportResult result) {
                dialog.dispose();
                statusBarPanel.hideProgress();
                refreshLibraryView();
                if (result != null) {
                    statusBarPanel.setStatus(I18nManager.getInstance().format(
                            "toast.import_detail",
                            result.addedCount(),
                            result.updatedCount(),
                            result.skippedCount()
                    ));
                } else {
                    statusBarPanel.setStatus(I18nManager.getInstance().get("status.ready"));
                }
            }

            @Override
            public void onCompleted(int totalImportedCount) {
                onCompleted(new ImportResult(totalImportedCount, 0, 0));
            }

            @Override
            public void onCancelled(int partialImportedCount) {
                dialog.dispose();
                statusBarPanel.hideProgress();
                refreshLibraryView();
                statusBarPanel.setStatus(I18nManager.getInstance().get("status.ready"));
            }

            @Override
            public void onError(Throwable error) {
                dialog.dispose();
                statusBarPanel.hideProgress();
                statusBarPanel.setStatus(I18nManager.getInstance().format("common.error", error.getMessage()));
            }
        });

        dialog.setOnCancel(task::cancel);
        dialog.setOnBackground(() -> statusBarPanel.showProgress(true, 0, 100));

        dialog.setVisible(true);
    }

    /**
     * Invalidate cached aggregated albums so they are recomputed upon next request.
     */
    public void invalidateAlbumCache() {
        this.cachedAlbums = null;
    }

    /**
     * Retrieves cached album list or computes it from library tracks if cache is empty.
     */
    public List<Album> getOrComputeAlbums() {
        List<Album> local = this.cachedAlbums;
        if (local == null) {
            local = Album.fromTracks(this.library.getTracks());
            this.cachedAlbums = local;
        }
        return local;
    }

    /**
     * Synchronizes view tables and summary panels with current Library content.
     */
    public void refreshLibraryView() {
        invalidateAlbumCache();
        if (libraryController != null) {
            libraryController.refreshCurrentView();
            libraryController.syncPlaylistsToSidebar();
        } else {
            List<Track> tracks = new ArrayList<>(library.getTracks());
            trackTablePanel.setTracks(tracks);
        }
        if (searchIndex != null) {
            searchIndex.clear();
            searchIndex.indexTracks(library.getTracks());
        }
        if (isAlbumGridActive) {
            showAlbumGrid();
        } else if (albumHeaderPanel != null && albumHeaderPanel.isVisible() && albumHeaderPanel.getAlbum() != null) {
            Album current = albumHeaderPanel.getAlbum();
            List<Album> refreshedAlbums = getOrComputeAlbums();
            Album match = null;
            for (Album a : refreshedAlbums) {
                if (a.getTitle().equalsIgnoreCase(current.getTitle()) &&
                    a.getArtist().equalsIgnoreCase(current.getArtist())) {
                    match = a;
                    break;
                }
            }
            if (match != null) {
                openAlbumDetail(match);
            } else {
                showAlbumGrid();
            }
        }
        if (searchController != null) {
            searchController.applyFilter();
        }
        updateStatusBarSummary();
    }

    /**
     * Navigates to album grid view displaying all library albums, resetting scroll position.
     */
    public void showAlbumGrid() {
        showAlbumGrid(false);
    }

    /**
     * Navigates to album grid view displaying all library albums, optionally restoring scroll position.
     *
     * @param restoreScroll true to restore the previous scroll position and selected album
     */
    public void showAlbumGrid(boolean restoreScroll) {
        isAlbumGridActive = true;
        if (albumHeaderPanel != null) {
            albumHeaderPanel.setVisible(false);
        }
        if (albumGridViewPanel != null) {
            List<Album> albums = getOrComputeAlbums();
            String currentQuery = (controlBarPanel != null && controlBarPanel.getSearchField() != null)
                    ? controlBarPanel.getSearchField().getText() : null;

            if (restoreScroll) {
                // If albums have not changed, avoid re-rendering to achieve instant response and preserve scroll
                if (!albums.equals(albumGridViewPanel.getAllAlbums())) {
                    albumGridViewPanel.setAlbums(albums, true);
                    if (currentQuery != null && !currentQuery.isBlank()) {
                        albumGridViewPanel.filterAlbums(currentQuery);
                    }
                }
            } else {
                albumGridViewPanel.setAlbums(albums, false);
                if (currentQuery != null && !currentQuery.isBlank()) {
                    albumGridViewPanel.filterAlbums(currentQuery);
                }
            }
        }
        if (cardLayout != null && mainContentPanel != null) {
            cardLayout.show(mainContentPanel, CARD_ALBUMS);
        }
        if (restoreScroll && albumGridViewPanel != null) {
            albumGridViewPanel.restoreScrollPosition();
        }
        updateStatusBarSummary();
    }

    /**
     * Navigates back to album grid view from album detail, preserving scroll position.
     */
    public void returnFromAlbumDetail() {
        showAlbumGrid(true);
    }

    /**
     * Navigates to album detail view displaying the album header and its tracks.
     *
     * @param album selected album
     */
    public void openAlbumDetail(Album album) {
        if (album == null) {
            return;
        }
        if (albumGridViewPanel != null) {
            albumGridViewPanel.saveScrollPosition(album);
        }
        isAlbumGridActive = false;
        if (albumHeaderPanel != null) {
            albumHeaderPanel.setAlbum(album);
            albumHeaderPanel.setVisible(true);
        }
        if (this.libraryController != null) {
            this.libraryController.showAlbum(album);
        } else {
            trackTablePanel.setPlaylistContext(false);
            trackTablePanel.setTracks(album.getTracks());
        }
        if (cardLayout != null && mainContentPanel != null) {
            cardLayout.show(mainContentPanel, CARD_TABLE);
        }
        updateStatusBarSummary();
    }

    private void updateStatusBarSummary() {
        if (statusBarPanel == null) {
            return;
        }
        if (isAlbumGridActive && albumGridViewPanel != null) {
            List<Album> displayed = albumGridViewPanel.getDisplayedAlbums();
            int totalTracks = 0;
            long totalDuration = 0;
            for (Album a : displayed) {
                totalTracks += a.getTrackCount();
                totalDuration += a.getTotalDurationSeconds();
            }
            statusBarPanel.updateSummary(totalTracks, totalDuration, 0);
            return;
        }
        if (trackTablePanel != null) {
            TrackTablePanel.TableSummary summary = trackTablePanel.getVisibleSummary();
            statusBarPanel.updateSummary(summary.trackCount(), summary.totalDurationSeconds(), summary.totalFileSizeBytes());
        }
    }

    /**
     * Updates all localized texts across the window, menus, and sub-panels.
     */
    public void updateLanguage() {
        updateTitle();

        if (appMenuBar != null) {
            appMenuBar.updateLanguage();
        }

        // Propagate to panels
        controlBarPanel.updateLanguage();
        sidebarPanel.updateLanguage();
        trackTablePanel.updateLanguage();
        if (albumGridViewPanel != null) {
            albumGridViewPanel.updateLanguage();
        }
        if (albumHeaderPanel != null) {
            albumHeaderPanel.updateLanguage();
        }
        statusBarPanel.updateLanguage();

        updateLanguageSelection();
    }

    private void updateRepeatMenuSelection(RepeatMode mode) {
        if (appMenuBar != null) {
            appMenuBar.updateRepeatMode(mode);
        }
    }

    private void updateThemeSelection() {
        if (appMenuBar != null) {
            appMenuBar.updateThemeSelection();
        }
    }

    private void updateLanguageSelection() {
        if (appMenuBar != null) {
            appMenuBar.updateLanguageSelection();
        }
    }

    @Override
    public void dispose() {
        try {
            SettingsManager.getInstance().saveFrom(this);
        } catch (Exception ex) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to save settings on dispose: " + ex.getMessage(), ex);
        }
        if (folderWatcherService != null) {
            folderWatcherService.stop();
        }
        if (importService != null) {
            importService.shutdown();
        }
        if (playerController != null) {
            playerController.dispose();
        }
        if (audioPlayer != null) {
            audioPlayer.close();
        }
        if (controlBarPanel != null) {
            controlBarPanel.dispose();
        }
        if (searchController != null) {
            searchController.dispose();
        }
        if (libraryController != null) {
            libraryController.dispose();
        }
        if (miniPlayerFrame != null) {
            miniPlayerFrame.dispose();
        }
        I18nManager.getInstance().removeLanguageChangeListener(languageListener);
        ThemeManager.getInstance().removeThemeChangeListener(themeListener);
        Main.removeShutdownHook();
        super.dispose();
    }

    // --- Panel & Service Getters ---

    public Library getLibrary() {
        return library;
    }

    public LibraryImportService getImportService() {
        return importService;
    }

    public FolderWatcherService getFolderWatcherService() {
        return folderWatcherService;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public PlayerController getPlayerController() {
        return playerController;
    }

    public ControlBarPanel getControlBarPanel() {
        return controlBarPanel;
    }

    public SidebarPanel getSidebarPanel() {
        return sidebarPanel;
    }

    public TrackTablePanel getTrackTablePanel() {
        return trackTablePanel;
    }

    public StatusBarPanel getStatusBarPanel() {
        return statusBarPanel;
    }

    public JSplitPane getSplitPane() {
        return splitPane;
    }

    public KeyboardShortcutManager getShortcutManager() {
        return shortcutManager;
    }

    public AppMenuBar getAppMenuBar() {
        return appMenuBar;
    }

    public JMenu getControlsMenu() {
        return appMenuBar != null ? appMenuBar.getControlsMenu() : null;
    }

    public JCheckBoxMenuItem getShuffleMenuItem() {
        return appMenuBar != null ? appMenuBar.getShuffleMenuItem() : null;
    }

    public JMenu getRepeatSubMenu() {
        return appMenuBar != null ? appMenuBar.getRepeatSubMenu() : null;
    }

    public JRadioButtonMenuItem getRepeatOffItem() {
        return appMenuBar != null ? appMenuBar.getRepeatOffItem() : null;
    }

    public JRadioButtonMenuItem getRepeatAllItem() {
        return appMenuBar != null ? appMenuBar.getRepeatAllItem() : null;
    }

    public JRadioButtonMenuItem getRepeatOneItem() {
        return appMenuBar != null ? appMenuBar.getRepeatOneItem() : null;
    }

    public FastSearchIndex getSearchIndex() {
        return searchIndex;
    }

    public SearchController getSearchController() {
        return searchController;
    }

    public LibraryController getLibraryController() {
        return libraryController;
    }

    public MiniPlayerFrame getMiniPlayerFrame() {
        return miniPlayerFrame;
    }

    public JMenuItem getEditTagsItem() {
        return appMenuBar != null ? appMenuBar.getEditTagsItem() : null;
    }

    public AlbumGridViewPanel getAlbumGridViewPanel() {
        return albumGridViewPanel;
    }

    public AlbumHeaderPanel getAlbumHeaderPanel() {
        return albumHeaderPanel;
    }

    public boolean isAlbumGridActive() {
        return isAlbumGridActive;
    }

    public JPanel getMainContentPanel() {
        return mainContentPanel;
    }

    public CardLayout getCardLayout() {
        return cardLayout;
    }

    public JMenuItem getRescanLibraryItem() {
        return appMenuBar != null ? appMenuBar.getRescanLibraryItem() : null;
    }

    // ==========================================
    // Account & Folder Management Integration
    // ==========================================

    private void updateTitle() {
        I18nManager i18n = I18nManager.getInstance();
        String appName = i18n.get("app.name");
        if (currentAccount != null && currentAccount.getName() != null && !currentAccount.getName().isBlank()
                && !Account.DEFAULT_ACCOUNT_ID.equals(currentAccount.getId())) {
            setTitle(appName + " - [" + currentAccount.getName() + "]");
        } else {
            setTitle(appName);
        }
    }

    private void showNewPlaylistFolderDialog() {
        NewPlaylistFolderDialog dialog = new NewPlaylistFolderDialog(this, folderName -> {
            if (folderName != null && !folderName.isBlank()) {
                ensureDbRepositories();
                if (folderRepository != null && currentAccount != null) {
                    try {
                        PlaylistFolder folder = new PlaylistFolder.Builder()
                                .name(folderName)
                                .accountId(currentAccount.getId())
                                .build();
                        folderRepository.save(folder);
                        refreshPlaylistsWithFolders();
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.ERROR, "Failed to save playlist folder: " + e.getMessage(), e);
                    }
                }
            }
        });
        dialog.setVisible(true);
    }

    public void refreshPlaylistsWithFolders() {
        Map<String, String> folderMap = loadFolderNamesMap();
        sidebarPanel.setPlaylists(library.getPlaylists(), folderMap);
    }

    private Map<String, String> loadFolderNamesMap() {
        Map<String, String> map = new HashMap<>();
        ensureDbRepositories();
        if (folderRepository != null && currentAccount != null) {
            try {
                List<PlaylistFolder> folders = folderRepository.findByAccountId(currentAccount.getId());
                for (PlaylistFolder f : folders) {
                    map.put(f.getId(), f.getName());
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to load playlist folders: " + e.getMessage(), e);
            }
        }
        return map;
    }

    public void showAccountManagerDialog() {
        ensureDbRepositories();
        if (accountRepository == null) {
            JOptionPane.showMessageDialog(this,
                    I18nManager.getText("dialog.account.no_db", "Account management requires an active database."),
                    I18nManager.getText("dialog.account.title", "Account Manager"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        AccountManagerDialog dialog = new AccountManagerDialog(
                this,
                accountRepository,
                currentAccount != null ? currentAccount.getId() : Account.DEFAULT_ACCOUNT_ID,
                this::switchAccount
        );
        dialog.setVisible(true);
    }

    public void switchAccount(Account targetAccount) {
        if (targetAccount == null || (currentAccount != null && Objects.equals(currentAccount.getId(), targetAccount.getId()))) {
            return;
        }

        // 1. Stop active playback
        if (playerController != null) {
            playerController.stop();
        }

        // 2. Persist current library data
        if (storage != null) {
            try {
                storage.save(this.library, this.storagePath);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to persist library on account switch: " + e.getMessage(), e);
            }
        }

        // 3. Switch account and re-instantiate storage
        this.currentAccount = targetAccount;
        if (dbManager != null) {
            this.storage = new SqliteLibraryStorage(dbManager, targetAccount.getId());
            if (libraryController != null) {
                libraryController.setStorage(this.storage);
            }
        }

        // 4. Load target account library
        Library loadedLibrary;
        if (this.storage != null) {
            try {
                loadedLibrary = this.storage.load(this.storagePath);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to load target account library: " + e.getMessage(), e);
                loadedLibrary = new Library();
            }
        } else {
            loadedLibrary = new Library();
        }

        // 5. Update library domain model
        this.library.clear();
        this.library.addTracks(loadedLibrary.getTracks());
        for (Playlist p : loadedLibrary.getPlaylists()) {
            this.library.addPlaylist(p);
        }

        // 6. Refresh search index
        this.searchIndex.clear();
        this.searchIndex.indexTracks(this.library.getTracks());

        // 7. Update UI views
        refreshPlaylistsWithFolders();
        this.sidebarPanel.selectSection(SidebarPanel.LibrarySection.ALL_SONGS);
        this.trackTablePanel.setTracks(new ArrayList<>(this.library.getTracks()));
        updateTitle();
        updateStatusBarSummary();
    }

    private void ensureDbRepositories() {
        if (dbManager == null) {
            try {
                dbManager = new DatabaseManager(Main.getDatabasePath());
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not create database manager: " + e.getMessage(), e);
                return;
            }
        }
        if (accountRepository == null && dbManager != null) {
            try {
                accountRepository = new SqliteAccountRepository(dbManager);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not create account repository: " + e.getMessage(), e);
            }
        }
        if (folderRepository == null && dbManager != null) {
            try {
                folderRepository = new SqlitePlaylistFolderRepository(dbManager);
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Could not create folder repository: " + e.getMessage(), e);
            }
        }
    }

    public Account getCurrentAccount() {
        return currentAccount;
    }

    public void setCurrentAccount(Account currentAccount) {
        this.currentAccount = currentAccount;
        updateTitle();
    }

    public JMenu getAccountMenu() {
        return appMenuBar != null ? appMenuBar.getAccountMenu() : null;
    }

    public JMenuItem getSwitchAccountItem() {
        return appMenuBar != null ? appMenuBar.getSwitchAccountItem() : null;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public LibraryStorage getStorage() {
        return storage;
    }

    public SqlitePlaylistFolderRepository getFolderRepository() {
        return folderRepository;
    }

    public SqliteAccountRepository getAccountRepository() {
        return accountRepository;
    }
}
