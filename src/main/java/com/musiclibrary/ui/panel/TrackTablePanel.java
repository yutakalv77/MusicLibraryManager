package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Track;
import com.musiclibrary.ui.table.TrackTableModel;
import com.musiclibrary.util.TimeFormatter;

import com.musiclibrary.ui.dnd.TrackTransferData;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Center table panel displaying tracks with columns for track number, title, artist,
 * album, duration, genre, year, and rating.
 * Fully integrates high-performance TrackTableModel with zero-allocation flyweight cell renderers
 * and right-click context menu.
 */
public class TrackTablePanel extends JPanel {

    private final JTable table;
    private final JScrollPane scrollPane;
    private final TrackTableModel tableModel;
    private final TableRowSorter<TrackTableModel> rowSorter;

    // Context Menu
    private final JPopupMenu contextMenu;
    private final JMenuItem playMenuItem;
    private final JMenuItem playNextMenuItem;
    private final JMenuItem getInfoMenuItem;
    private final JMenuItem editTagsMenuItem;
    private final JMenuItem deleteMenuItem;

    // Action callbacks
    private Consumer<Track> onPlayAction;
    private Consumer<Track> onPlayNextAction;
    private Consumer<Track> onGetInfoAction;
    private Consumer<List<Track>> onEditTagsAction;
    private Consumer<Track> onDeleteAction;
    private Consumer<List<Track>> onDeleteTracksAction;
    private boolean isPlaylistContext = false;

    // Shared Flyweight Cell Renderers
    private static final NumericCellRenderer TRACK_NUMBER_RENDERER = new NumericCellRenderer(SwingConstants.CENTER, "-");
    private static final NumericCellRenderer YEAR_RENDERER = new NumericCellRenderer(SwingConstants.CENTER, "-");
    private static final DurationCellRenderer DURATION_RENDERER = new DurationCellRenderer();
    private static final RatingCellRenderer RATING_RENDERER = new RatingCellRenderer();

    // Column preferred width constants
    private static final int COLUMN_WIDTH_TRACK_NUMBER = 45;
    private static final int COLUMN_WIDTH_TITLE = 220;
    private static final int COLUMN_WIDTH_ARTIST = 160;
    private static final int COLUMN_WIDTH_ALBUM = 160;
    private static final int COLUMN_WIDTH_DURATION = 65;
    private static final int COLUMN_WIDTH_GENRE = 95;
    private static final int COLUMN_WIDTH_YEAR = 60;
    private static final int COLUMN_WIDTH_RATING = 75;

    public TrackTablePanel() {
        this(new TrackTableModel());
    }

    public TrackTablePanel(TrackTableModel model) {
        setLayout(new BorderLayout());

        this.tableModel = model != null ? model : new TrackTableModel();
        this.table = new JTable(this.tableModel);
        this.rowSorter = new TableRowSorter<>(this.tableModel);
        this.table.setRowSorter(this.rowSorter);

        this.table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.table.setRowHeight(26);
        this.table.setShowHorizontalLines(true);
        this.table.setShowVerticalLines(false);
        this.table.setFillsViewportHeight(true);

        // Enable Drag-and-Drop row export to playlists
        this.table.setDragEnabled(true);
        this.table.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                List<Track> selected = getSelectedTracks();
                if (selected.isEmpty()) {
                    return null;
                }
                List<String> ids = selected.stream().map(Track::getId).toList();
                return new TrackTransferData(ids);
            }
        });

        // Configure high-performance cell renderers
        setupCellRenderers();

        // Adjust column preferred widths
        TableColumnModel columnModel = table.getColumnModel();
        if (columnModel.getColumnCount() >= 8) {
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_TRACK_NUMBER).setPreferredWidth(COLUMN_WIDTH_TRACK_NUMBER);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_TITLE).setPreferredWidth(COLUMN_WIDTH_TITLE);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_ARTIST).setPreferredWidth(COLUMN_WIDTH_ARTIST);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_ALBUM).setPreferredWidth(COLUMN_WIDTH_ALBUM);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_DURATION).setPreferredWidth(COLUMN_WIDTH_DURATION);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_GENRE).setPreferredWidth(COLUMN_WIDTH_GENRE);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_YEAR).setPreferredWidth(COLUMN_WIDTH_YEAR);
            columnModel.getColumn(TrackTableModel.COLUMN_INDEX_RATING).setPreferredWidth(COLUMN_WIDTH_RATING);
        }

        // Context menu initialization
        contextMenu = new JPopupMenu();
        playMenuItem = new JMenuItem();
        playNextMenuItem = new JMenuItem();
        getInfoMenuItem = new JMenuItem();
        editTagsMenuItem = new JMenuItem();
        deleteMenuItem = new JMenuItem();

        setupContextMenu();

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        updateLanguage();
    }

    private void setupCellRenderers() {
        TableColumnModel colModel = table.getColumnModel();
        if (colModel.getColumnCount() >= 8) {
            colModel.getColumn(TrackTableModel.COLUMN_INDEX_TRACK_NUMBER).setCellRenderer(TRACK_NUMBER_RENDERER);
            colModel.getColumn(TrackTableModel.COLUMN_INDEX_TITLE).setCellRenderer(new TitleCellRenderer());
            colModel.getColumn(TrackTableModel.COLUMN_INDEX_DURATION).setCellRenderer(DURATION_RENDERER);
            colModel.getColumn(TrackTableModel.COLUMN_INDEX_YEAR).setCellRenderer(YEAR_RENDERER);
            colModel.getColumn(TrackTableModel.COLUMN_INDEX_RATING).setCellRenderer(RATING_RENDERER);
        }
    }

    private void setupContextMenu() {
        playMenuItem.addActionListener(e -> playSelectedTrack());

        playNextMenuItem.addActionListener(e -> {
            Track selected = getSelectedTrack();
            if (selected != null && onPlayNextAction != null) {
                onPlayNextAction.accept(selected);
            }
        });

        getInfoMenuItem.addActionListener(e -> {
            triggerEditTagsAction();
            Track selected = getSelectedTrack();
            if (selected != null && onGetInfoAction != null) {
                onGetInfoAction.accept(selected);
            }
        });

        editTagsMenuItem.addActionListener(e -> triggerEditTagsAction());

        deleteMenuItem.addActionListener(e -> triggerDeleteAction());

        contextMenu.add(playMenuItem);
        contextMenu.add(playNextMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(getInfoMenuItem);
        contextMenu.add(editTagsMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(deleteMenuItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleContextMenuTrigger(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleContextMenuTrigger(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        playSelectedTrack();
                    }
                }
            }
        });

        // KeyStroke bindings: ENTER -> play, DELETE -> remove, CTRL+I -> edit tags
        KeyStroke enterStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enterStroke, "playSelectedTrack");
        table.getInputMap(JComponent.WHEN_FOCUSED).put(enterStroke, "playSelectedTrack");
        table.getActionMap().put("playSelectedTrack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playSelectedTrack();
            }
        });

        KeyStroke deleteStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(deleteStroke, "deleteSelectedTracks");
        table.getInputMap(JComponent.WHEN_FOCUSED).put(deleteStroke, "deleteSelectedTracks");
        table.getActionMap().put("deleteSelectedTracks", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                triggerDeleteAction();
            }
        });

        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke editTagsStroke = KeyStroke.getKeyStroke(KeyEvent.VK_I, mask);
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(editTagsStroke, "editSelectedTags");
        table.getInputMap(JComponent.WHEN_FOCUSED).put(editTagsStroke, "editSelectedTags");
        table.getActionMap().put("editSelectedTags", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                triggerEditTagsAction();
            }
        });

        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    playSelectedTrack();
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    e.consume();
                    triggerDeleteAction();
                } else if (e.getKeyCode() == KeyEvent.VK_I && (e.isControlDown() || e.isMetaDown())) {
                    e.consume();
                    triggerEditTagsAction();
                }
            }
        });
    }

    /**
     * Triggers track deletion action based on current row selection.
     */
    public void triggerDeleteAction() {
        List<Track> selected = getSelectedTracks();
        if (!selected.isEmpty()) {
            if (onDeleteTracksAction != null) {
                onDeleteTracksAction.accept(selected);
            } else if (onDeleteAction != null) {
                onDeleteAction.accept(selected.get(0));
            }
        }
    }

    /**
     * Triggers tag editing action for all currently selected tracks.
     */
    public void triggerEditTagsAction() {
        List<Track> selected = getSelectedTracks();
        if (!selected.isEmpty() && onEditTagsAction != null) {
            onEditTagsAction.accept(selected);
        }
    }

    /**
     * Plays currently selected track by firing onPlayAction callback.
     */
    public void playSelectedTrack() {
        Track selected = getSelectedTrack();
        if (selected != null && onPlayAction != null) {
            onPlayAction.accept(selected);
        }
    }

    /**
     * Configures whether the table is displaying a playlist or library section to update action labels.
     *
     * @param inPlaylist true if viewing a playlist
     */
    public void setPlaylistContext(boolean inPlaylist) {
        this.isPlaylistContext = inPlaylist;
        updateLanguage();
    }

    public boolean isPlaylistContext() {
        return isPlaylistContext;
    }

    public void setOnDeleteTracksAction(Consumer<List<Track>> onDeleteTracksAction) {
        this.onDeleteTracksAction = onDeleteTracksAction;
    }

    private void handleContextMenuTrigger(MouseEvent e) {
        if (e.isPopupTrigger()) {
            int row = table.rowAtPoint(e.getPoint());
            if (row != -1) {
                if (!table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                contextMenu.show(table, e.getX(), e.getY());
            }
        }
    }

    /**
     * Updates column header titles and context menu items from I18nManager.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();
        TableColumnModel columnModel = table.getColumnModel();

        for (int i = 0; i < TrackTableModel.COLUMN_KEYS.length && i < columnModel.getColumnCount(); i++) {
            columnModel.getColumn(i).setHeaderValue(i18n.get(TrackTableModel.COLUMN_KEYS[i]));
        }
        table.getTableHeader().repaint();

        playMenuItem.setText(i18n.get("table.menu.play"));
        playNextMenuItem.setText(i18n.get("table.menu.play_next"));
        getInfoMenuItem.setText(i18n.get("table.menu.get_info"));
        editTagsMenuItem.setText(i18n.get("table.menu.edit_tags"));
        deleteMenuItem.setText(i18n.get(isPlaylistContext ? "table.menu.delete_from_playlist" : "table.menu.delete"));
    }

    /**
     * Updates tracks displayed in the table.
     *
     * @param tracks list of tracks
     */
    public void setTracks(List<Track> tracks) {
        tableModel.setTracks(tracks);
    }

    /**
     * Appends a single track to the table model.
     *
     * @param track track to append
     */
    public void addTrack(Track track) {
        tableModel.addTrack(track);
    }

    /**
     * Appends a batch of tracks with single-event range notification for optimal rendering.
     *
     * @param tracks batch of tracks to append
     */
    public void addTracks(List<Track> tracks) {
        tableModel.addTracks(tracks);
    }

    /**
     * Updates an existing track in the table without reloading the entire model.
     *
     * @param track updated track entity
     * @return true if track was updated
     */
    public boolean updateTrack(Track track) {
        return tableModel.updateTrack(track);
    }

    /**
     * Removes track at specified row index.
     *
     * @param rowIndex model row index
     * @return removed Track
     */
    public Track removeTrack(int rowIndex) {
        return tableModel.removeTrack(rowIndex);
    }

    /**
     * Clears all tracks from the table.
     */
    public void clear() {
        tableModel.clear();
    }

    /**
     * Returns the currently selected Track, or null if none is selected.
     *
     * @return selected Track or null
     */
    public Track getSelectedTrack() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return tableModel.getTrackAt(modelRow);
    }

    /**
     * Returns a list of all currently selected tracks.
     *
     * @return selected tracks
     */
    public List<Track> getSelectedTracks() {
        int[] selectedRows = table.getSelectedRows();
        List<Track> selected = new ArrayList<>(selectedRows.length);
        for (int row : selectedRows) {
            int modelRow = table.convertRowIndexToModel(row);
            Track t = tableModel.getTrackAt(modelRow);
            if (t != null) {
                selected.add(t);
            }
        }
        return selected;
    }

    /**
     * Immutable summary record of tracks currently visible in the table.
     *
     * @param trackCount           number of visible tracks
     * @param totalDurationSeconds total duration of visible tracks in seconds
     * @param totalFileSizeBytes   total file size of visible tracks in bytes
     */
    public record TableSummary(int trackCount, long totalDurationSeconds, long totalFileSizeBytes) {}

    /**
     * Returns a list of all currently visible tracks according to active filter and sort orders.
     *
     * @return list of visible tracks in view order
     */
    public List<Track> getVisibleTracks() {
        int count = table.getRowCount();
        List<Track> visible = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int modelRow = table.convertRowIndexToModel(i);
            Track track = tableModel.getTrackAt(modelRow);
            if (track != null) {
                visible.add(track);
            }
        }
        return visible;
    }

    /**
     * Computes aggregate summary metrics for currently visible tracks.
     *
     * @return TableSummary record
     */
    public TableSummary getVisibleSummary() {
        int count = table.getRowCount();
        long totalDuration = 0;
        long totalSize = 0;
        for (int i = 0; i < count; i++) {
            int modelRow = table.convertRowIndexToModel(i);
            Track t = tableModel.getTrackAt(modelRow);
            if (t != null) {
                totalDuration += t.getDurationSeconds();
                totalSize += t.getFileSizeBytes();
            }
        }
        return new TableSummary(count, totalDuration, totalSize);
    }

    public TrackTableModel getTableModel() {
        return tableModel;
    }

    public TableRowSorter<TrackTableModel> getRowSorter() {
        return rowSorter;
    }

    public JTable getTable() {
        return table;
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public JPopupMenu getContextMenu() {
        return contextMenu;
    }

    public int getTrackCount() {
        return tableModel.getRowCount();
    }

    // --- Action Callback Setters ---

    public void setOnPlayAction(Consumer<Track> onPlayAction) {
        this.onPlayAction = onPlayAction;
    }

    public void setOnPlayNextAction(Consumer<Track> onPlayNextAction) {
        this.onPlayNextAction = onPlayNextAction;
    }

    public void setOnGetInfoAction(Consumer<Track> onGetInfoAction) {
        this.onGetInfoAction = onGetInfoAction;
    }

    public void setOnDeleteAction(Consumer<Track> onDeleteAction) {
        this.onDeleteAction = onDeleteAction;
    }

    public void setOnEditTagsAction(Consumer<List<Track>> onEditTagsAction) {
        this.onEditTagsAction = onEditTagsAction;
    }

    public JMenuItem getPlayMenuItem() {
        return playMenuItem;
    }

    public JMenuItem getPlayNextMenuItem() {
        return playNextMenuItem;
    }

    public JMenuItem getGetInfoMenuItem() {
        return getInfoMenuItem;
    }

    public JMenuItem getEditTagsMenuItem() {
        return editTagsMenuItem;
    }

    public JMenuItem getDeleteMenuItem() {
        return deleteMenuItem;
    }

    // --- High-Performance Cell Renderers (Flyweight Pattern) ---

    private static class NumericCellRenderer extends DefaultTableCellRenderer {
        private final String emptyText;

        public NumericCellRenderer(int alignment, String emptyText) {
            this.emptyText = emptyText;
            setHorizontalAlignment(alignment);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Number num) {
                long val = num.longValue();
                if (val <= 0) {
                    setText(emptyText);
                } else {
                    setText(String.valueOf(val));
                }
            } else {
                setText(value != null ? value.toString() : emptyText);
            }
        }
    }

    private static class DurationCellRenderer extends DefaultTableCellRenderer {
        public DurationCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Number num) {
                setText(TimeFormatter.format(num.longValue()));
            } else {
                setText("00:00");
            }
        }
    }

    private static class RatingCellRenderer extends DefaultTableCellRenderer {
        private static final String[] STARS = {
                "—",
                "★",
                "★★",
                "★★★",
                "★★★★",
                "★★★★★"
        };

        public RatingCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Number num) {
                int rating = num.intValue();
                if (rating >= 1 && rating <= 5) {
                    setText(STARS[rating]);
                } else {
                    setText(STARS[0]);
                }
            } else {
                setText(STARS[0]);
            }
        }
    }

    /**
     * Updates missing flags on current table tracks and repaints table cells.
     *
     * @param missingTracks collection of currently detected missing tracks
     */
    public void updateMissingTracks(Collection<Track> missingTracks) {
        Set<String> missingIds = missingTracks != null
                ? missingTracks.stream().map(Track::getId).collect(Collectors.toSet())
                : Collections.emptySet();
        for (Track t : tableModel.getTracks()) {
            t.setMissing(missingIds.contains(t.getId()));
        }
        tableModel.fireTableDataChanged();
    }

    private class TitleCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            Track track = tableModel.getTrackAt(modelRow);
            if (track != null && track.isMissing()) {
                setText("⚠️ " + (value != null ? value.toString() : ""));
                setToolTipText(I18nManager.getInstance().get("missing.warning_tooltip"));
                if (!isSelected) {
                    setForeground(new Color(220, 80, 80));
                }
            } else {
                setToolTipText(null);
            }
            return c;
        }
    }
}
