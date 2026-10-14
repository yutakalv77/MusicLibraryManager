package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Playlist;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sidebar panel displaying Library navigation items, user playlists,
 * and a button to create new playlists.
 */
public class SidebarPanel extends JPanel {

    public enum LibrarySection {
        ALL_SONGS,
        ARTISTS,
        ALBUMS,
        RECENTLY_ADDED
    }

    private final JLabel libraryHeaderLabel;
    private final JList<String> libraryList;
    private final DefaultListModel<String> libraryListModel;

    private final JLabel playlistsHeaderLabel;
    private final JList<String> playlistList;
    private final DefaultListModel<String> playlistListModel;
    private final List<Playlist> currentPlaylists;

    private final JButton newPlaylistButton;

    // Context Menu for Playlists
    private final JPopupMenu playlistContextMenu;
    private final JMenuItem playPlaylistItem;
    private final JMenuItem renamePlaylistItem;
    private final JMenuItem deletePlaylistItem;

    // Action Callbacks
    private Consumer<Playlist> onPlaylistPlayAction;
    private Consumer<Playlist> onPlaylistRenameAction;
    private Consumer<Playlist> onPlaylistDeleteAction;
    private Runnable onNewPlaylistAction;
    private Consumer<LibrarySection> onSectionSelectedAction;
    private Consumer<Playlist> onPlaylistSelectedAction;
    private BiConsumer<Playlist, List<String>> onTracksDroppedOnPlaylistAction;

    public SidebarPanel() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(210, 0));
        setMinimumSize(new Dimension(170, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        // Center container for Library and Playlists lists
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 1. Library Section
        libraryHeaderLabel = new JLabel();
        Font libFont = libraryHeaderLabel.getFont();
        libraryHeaderLabel.setFont(libFont != null ? libFont.deriveFont(Font.BOLD, 11f) : new Font(Font.SANS_SERIF, Font.BOLD, 11));
        libraryHeaderLabel.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.add(libraryHeaderLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        libraryListModel = new DefaultListModel<>();
        libraryList = new JList<>(libraryListModel);
        libraryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        libraryList.setAlignmentX(LEFT_ALIGNMENT);
        libraryList.setSelectedIndex(0);
        contentPanel.add(libraryList);

        contentPanel.add(Box.createVerticalStrut(16));

        // 2. Playlists Section
        playlistsHeaderLabel = new JLabel();
        Font plFont = playlistsHeaderLabel.getFont();
        playlistsHeaderLabel.setFont(plFont != null ? plFont.deriveFont(Font.BOLD, 11f) : new Font(Font.SANS_SERIF, Font.BOLD, 11));
        playlistsHeaderLabel.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.add(playlistsHeaderLabel);
        contentPanel.add(Box.createVerticalStrut(4));

        playlistListModel = new DefaultListModel<>();
        playlistList = new JList<>(playlistListModel);
        playlistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlistList.setAlignmentX(LEFT_ALIGNMENT);
        this.currentPlaylists = new ArrayList<>();

        JScrollPane playlistScrollPane = new JScrollPane(playlistList);
        playlistScrollPane.setBorder(BorderFactory.createEmptyBorder());
        playlistScrollPane.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.add(playlistScrollPane);

        add(contentPanel, BorderLayout.CENTER);

        // 3. Bottom: New Playlist Button
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));

        newPlaylistButton = new JButton();
        newPlaylistButton.setPreferredSize(new Dimension(0, 32));
        newPlaylistButton.addActionListener(e -> {
            if (onNewPlaylistAction != null) {
                onNewPlaylistAction.run();
            }
        });
        bottomPanel.add(newPlaylistButton, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        // 4. Playlist Context Menu
        playlistContextMenu = new JPopupMenu();
        playPlaylistItem = new JMenuItem();
        renamePlaylistItem = new JMenuItem();
        deletePlaylistItem = new JMenuItem();

        playPlaylistItem.addActionListener(e -> {
            Playlist p = getSelectedPlaylist();
            if (p != null && onPlaylistPlayAction != null) {
                onPlaylistPlayAction.accept(p);
            }
        });
        renamePlaylistItem.addActionListener(e -> {
            Playlist p = getSelectedPlaylist();
            if (p != null && onPlaylistRenameAction != null) {
                onPlaylistRenameAction.accept(p);
            }
        });
        deletePlaylistItem.addActionListener(e -> {
            Playlist p = getSelectedPlaylist();
            if (p != null && onPlaylistDeleteAction != null) {
                onPlaylistDeleteAction.accept(p);
            }
        });

        playlistContextMenu.add(playPlaylistItem);
        playlistContextMenu.addSeparator();
        playlistContextMenu.add(renamePlaylistItem);
        playlistContextMenu.add(deletePlaylistItem);

        playlistList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePlaylistContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePlaylistContextMenu(e);
            }
        });

        // 5. Mutually exclusive selection and navigation callbacks
        libraryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = libraryList.getSelectedIndex();
                if (idx != -1) {
                    playlistList.clearSelection();
                    if (onSectionSelectedAction != null) {
                        LibrarySection[] sections = LibrarySection.values();
                        if (idx >= 0 && idx < sections.length) {
                            onSectionSelectedAction.accept(sections[idx]);
                        }
                    }
                }
            }
        });

        libraryList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int idx = libraryList.locationToIndex(e.getPoint());
                    if (idx != -1) {
                        var bounds = libraryList.getCellBounds(idx, idx);
                        if (bounds != null && bounds.contains(e.getPoint())) {
                            LibrarySection[] sections = LibrarySection.values();
                            if (idx >= 0 && idx < sections.length && onSectionSelectedAction != null) {
                                onSectionSelectedAction.accept(sections[idx]);
                            }
                        }
                    }
                }
            }
        });

        playlistList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = playlistList.getSelectedIndex();
                if (idx != -1) {
                    libraryList.clearSelection();
                    if (onPlaylistSelectedAction != null && idx >= 0 && idx < currentPlaylists.size()) {
                        onPlaylistSelectedAction.accept(currentPlaylists.get(idx));
                    }
                }
            }
        });

        updateLanguage();
    }

    private void handlePlaylistContextMenu(MouseEvent e) {
        if (e.isPopupTrigger()) {
            int index = playlistList.locationToIndex(e.getPoint());
            if (index >= 0 && index < currentPlaylists.size()) {
                var cellBounds = playlistList.getCellBounds(index, index);
                if (cellBounds != null && cellBounds.contains(e.getPoint())) {
                    playlistList.setSelectedIndex(index);
                    playlistContextMenu.show(playlistList, e.getX(), e.getY());
                }
            }
        }
    }

    /**
     * Updates localized header texts, list items, and button labels.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();

        libraryHeaderLabel.setText(i18n.get("sidebar.library"));
        playlistsHeaderLabel.setText(i18n.get("sidebar.playlists"));
        newPlaylistButton.setText("+ " + i18n.get("sidebar.new_playlist"));

        playPlaylistItem.setText(i18n.get("sidebar.playlist.play"));
        renamePlaylistItem.setText(i18n.get("sidebar.playlist.rename"));
        deletePlaylistItem.setText(i18n.get("sidebar.playlist.delete"));

        int selectedLibIndex = libraryList.getSelectedIndex();
        libraryListModel.clear();
        libraryListModel.addElement("🎵  " + i18n.get("sidebar.all_songs"));
        libraryListModel.addElement("👤  " + i18n.get("sidebar.artists"));
        libraryListModel.addElement("💿  " + i18n.get("sidebar.albums"));
        libraryListModel.addElement("🕒  " + i18n.get("sidebar.recently_added"));

        if (selectedLibIndex >= 0 && selectedLibIndex < libraryListModel.size()) {
            libraryList.setSelectedIndex(selectedLibIndex);
        }

        revalidate();
        repaint();
    }

    /**
     * Updates the displayed playlist names in the playlist list.
     *
     * @param playlists list of playlists
     */
    public void setPlaylists(List<Playlist> playlists) {
        this.currentPlaylists.clear();
        this.playlistListModel.clear();

        if (playlists != null) {
            this.currentPlaylists.addAll(playlists);
            for (Playlist playlist : playlists) {
                this.playlistListModel.addElement("📁  " + playlist.getName());
            }
        }
    }

    // --- Component Getters ---

    public JList<String> getLibraryList() {
        return libraryList;
    }

    public JList<String> getPlaylistList() {
        return playlistList;
    }

    public JButton getNewPlaylistButton() {
        return newPlaylistButton;
    }

    public List<Playlist> getCurrentPlaylists() {
        return currentPlaylists;
    }

    public Playlist getSelectedPlaylist() {
        int index = playlistList.getSelectedIndex();
        if (index >= 0 && index < currentPlaylists.size()) {
            return currentPlaylists.get(index);
        }
        return null;
    }

    /**
     * Selects a library section programmatically.
     *
     * @param section target section
     */
    public void selectSection(LibrarySection section) {
        if (section != null) {
            boolean sameIndex = (libraryList.getSelectedIndex() == section.ordinal());
            libraryList.setSelectedIndex(section.ordinal());
            if (sameIndex && onSectionSelectedAction != null) {
                onSectionSelectedAction.accept(section);
            }
        }
    }

    /**
     * Selects a playlist programmatically by its unique ID.
     *
     * @param playlistId target playlist ID
     */
    public void selectPlaylist(String playlistId) {
        if (playlistId == null) {
            playlistList.clearSelection();
            return;
        }
        for (int i = 0; i < currentPlaylists.size(); i++) {
            if (Objects.equals(currentPlaylists.get(i).getId(), playlistId)) {
                if (i >= 0 && i < playlistListModel.getSize()) {
                    playlistList.setSelectedIndex(i);
                    playlistList.ensureIndexIsVisible(i);
                }
                return;
            }
        }
    }

    public JPopupMenu getPlaylistContextMenu() {
        return playlistContextMenu;
    }

    public JMenuItem getPlayPlaylistItem() {
        return playPlaylistItem;
    }

    public JMenuItem getRenamePlaylistItem() {
        return renamePlaylistItem;
    }

    public JMenuItem getDeletePlaylistItem() {
        return deletePlaylistItem;
    }

    // --- Action Callback Setters ---

    public void setOnPlaylistPlayAction(Consumer<Playlist> onPlaylistPlayAction) {
        this.onPlaylistPlayAction = onPlaylistPlayAction;
    }

    public void setOnPlaylistRenameAction(Consumer<Playlist> onPlaylistRenameAction) {
        this.onPlaylistRenameAction = onPlaylistRenameAction;
    }

    public void setOnPlaylistDeleteAction(Consumer<Playlist> onPlaylistDeleteAction) {
        this.onPlaylistDeleteAction = onPlaylistDeleteAction;
    }

    public void setOnNewPlaylistAction(Runnable onNewPlaylistAction) {
        this.onNewPlaylistAction = onNewPlaylistAction;
    }

    public void setOnSectionSelectedAction(Consumer<LibrarySection> onSectionSelectedAction) {
        this.onSectionSelectedAction = onSectionSelectedAction;
    }

    public void setOnPlaylistSelectedAction(Consumer<Playlist> onPlaylistSelectedAction) {
        this.onPlaylistSelectedAction = onPlaylistSelectedAction;
    }

    public void setOnTracksDroppedOnPlaylistAction(BiConsumer<Playlist, List<String>> onTracksDroppedOnPlaylistAction) {
        this.onTracksDroppedOnPlaylistAction = onTracksDroppedOnPlaylistAction;
    }

    public BiConsumer<Playlist, List<String>> getOnTracksDroppedOnPlaylistAction() {
        return onTracksDroppedOnPlaylistAction;
    }
}
