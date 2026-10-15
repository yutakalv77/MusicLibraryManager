package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import com.musiclibrary.ui.dnd.TrackTransferData;
import com.musiclibrary.settings.UserPreferences;
import com.musiclibrary.ui.table.PlaybackStatusIcon;
import com.musiclibrary.ui.table.TrackTableModel;
import com.musiclibrary.util.TimeFormatter;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Center table panel displaying tracks with configurable columns, high-performance flyweight
 * cell renderers, row context menu, and column visibility toggle via header right-click context menu.
 */
public class TrackTablePanel extends JPanel {

    private final JTable table;
    private final JScrollPane scrollPane;
    private final TrackTableModel tableModel;
    private final TableRowSorter<TrackTableModel> rowSorter;

    // Table Columns & Visibility
    private final TableColumn[] allTableColumns;
    private final JPopupMenu headerPopupMenu;
    private final JCheckBoxMenuItem[] columnMenuItems;

    // Default visible columns: Status, Track#, Title, Artist, Album, Duration, Genre, Year, Rating
    public static final Set<Integer> DEFAULT_VISIBLE_COLUMNS = Set.of(
            TrackTableModel.COLUMN_INDEX_STATUS,
            TrackTableModel.COLUMN_INDEX_TRACK_NUMBER,
            TrackTableModel.COLUMN_INDEX_TITLE,
            TrackTableModel.COLUMN_INDEX_ARTIST,
            TrackTableModel.COLUMN_INDEX_ALBUM,
            TrackTableModel.COLUMN_INDEX_DURATION,
            TrackTableModel.COLUMN_INDEX_GENRE,
            TrackTableModel.COLUMN_INDEX_YEAR,
            TrackTableModel.COLUMN_INDEX_RATING
    );

    // Row Context Menu
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

    // Playback status indicator
    private volatile String currentPlayingTrackId = null;
    private volatile PlaybackState currentPlaybackState = PlaybackState.STOPPED;
    private final StatusCellRenderer statusRenderer = new StatusCellRenderer();

    // Shared Flyweight Cell Renderers
    private static final NumericCellRenderer TRACK_NUMBER_RENDERER = new NumericCellRenderer(SwingConstants.CENTER, "-");
    private static final NumericCellRenderer YEAR_RENDERER = new NumericCellRenderer(SwingConstants.CENTER, "-");
    private static final NumericCellRenderer DISC_NUMBER_RENDERER = new NumericCellRenderer(SwingConstants.CENTER, "-");
    private static final NumericCellRenderer BPM_RENDERER = new NumericCellRenderer(SwingConstants.CENTER, "-");
    private static final DurationCellRenderer DURATION_RENDERER = new DurationCellRenderer();
    private static final RatingCellRenderer RATING_RENDERER = new RatingCellRenderer();
    private static final BitrateCellRenderer BITRATE_RENDERER = new BitrateCellRenderer();
    private static final DateCellRenderer DATE_ADDED_RENDERER = new DateCellRenderer();
    private static final PlayCountCellRenderer PLAY_COUNT_RENDERER = new PlayCountCellRenderer();

    // Column preferred width constants
    private static final int COLUMN_WIDTH_STATUS = 30;
    private static final int COLUMN_WIDTH_TRACK_NUMBER = 80;
    private static final int COLUMN_WIDTH_TITLE = 220;
    private static final int COLUMN_WIDTH_ARTIST = 160;
    private static final int COLUMN_WIDTH_ALBUM_ARTIST = 160;
    private static final int COLUMN_WIDTH_ALBUM = 160;
    private static final int COLUMN_WIDTH_DURATION = 65;
    private static final int COLUMN_WIDTH_GENRE = 95;
    private static final int COLUMN_WIDTH_YEAR = 60;
    private static final int COLUMN_WIDTH_DISC_NUMBER = 50;
    private static final int COLUMN_WIDTH_BPM = 55;
    private static final int COLUMN_WIDTH_BITRATE = 75;
    private static final int COLUMN_WIDTH_DATE_ADDED = 130;
    private static final int COLUMN_WIDTH_PLAY_COUNT = 60;
    private static final int COLUMN_WIDTH_RATING = 75;

    public TrackTablePanel() {
        this(new TrackTableModel());
    }

    public TrackTablePanel(TrackTableModel model) {
        setLayout(new BorderLayout());

        this.tableModel = model != null ? model : new TrackTableModel();
        this.table = new JTable(this.tableModel);
        this.table.setAutoCreateColumnsFromModel(false);
        this.rowSorter = new TableRowSorter<>(this.tableModel);
        this.rowSorter.setSortable(TrackTableModel.COLUMN_INDEX_STATUS, false);
        this.table.setRowSorter(this.rowSorter);

        // Keep Status column permanently pinned to view index 0
        this.table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            private boolean adjusting = false;

            @Override
            public void columnMoved(TableColumnModelEvent e) {
                if (adjusting) {
                    return;
                }
                TableColumnModel colModel = table.getColumnModel();
                int statusViewIdx = -1;
                for (int i = 0; i < colModel.getColumnCount(); i++) {
                    if (colModel.getColumn(i).getModelIndex() == TrackTableModel.COLUMN_INDEX_STATUS) {
                        statusViewIdx = i;
                        break;
                    }
                }
                if (statusViewIdx > 0) {
                    adjusting = true;
                    try {
                        colModel.moveColumn(statusViewIdx, 0);
                    } finally {
                        adjusting = false;
                    }
                }
            }

            @Override public void columnAdded(TableColumnModelEvent e) {}
            @Override public void columnRemoved(TableColumnModelEvent e) {}
            @Override public void columnMarginChanged(ChangeEvent e) {}
            @Override public void columnSelectionChanged(ListSelectionEvent e) {}
        });

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

        // Initialize all table columns and column visibility popup menu
        int columnCount = TrackTableModel.COLUMN_KEYS.length;
        this.allTableColumns = new TableColumn[columnCount];
        this.columnMenuItems = new JCheckBoxMenuItem[columnCount];
        this.headerPopupMenu = new JPopupMenu();

        setupColumns();
        setupHeaderContextMenu();

        // Row context menu initialization
        contextMenu = new JPopupMenu();
        playMenuItem = new JMenuItem();
        playNextMenuItem = new JMenuItem();
        getInfoMenuItem = new JMenuItem();
        editTagsMenuItem = new JMenuItem();
        deleteMenuItem = new JMenuItem();

        setupRowContextMenu();

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        updateLanguage();
    }

    /**
     * Creates all 15 TableColumn instances with appropriate renderers and widths,
     * adding only the default visible columns into the active TableColumnModel.
     */
    private void setupColumns() {
        TableColumnModel colModel = table.getColumnModel();

        // Clear any existing columns from TableColumnModel
        while (colModel.getColumnCount() > 0) {
            colModel.removeColumn(colModel.getColumn(0));
        }

        for (int i = 0; i < TrackTableModel.COLUMN_KEYS.length; i++) {
            TableColumn col = new TableColumn(i);
            col.setPreferredWidth(getPreferredWidthForColumn(i));
            if (i == TrackTableModel.COLUMN_INDEX_STATUS) {
                col.setMinWidth(COLUMN_WIDTH_STATUS);
                col.setMaxWidth(COLUMN_WIDTH_STATUS);
                col.setResizable(false);
            }
            configureColumnRenderer(col, i);
            allTableColumns[i] = col;
        }

        // Add default visible columns in order: Status is always first
        if (DEFAULT_VISIBLE_COLUMNS.contains(TrackTableModel.COLUMN_INDEX_STATUS)) {
            colModel.addColumn(allTableColumns[TrackTableModel.COLUMN_INDEX_STATUS]);
        }
        for (int i = 0; i < TrackTableModel.COLUMN_KEYS.length; i++) {
            if (i != TrackTableModel.COLUMN_INDEX_STATUS && DEFAULT_VISIBLE_COLUMNS.contains(i)) {
                colModel.addColumn(allTableColumns[i]);
            }
        }
    }

    private int getPreferredWidthForColumn(int modelIndex) {
        return switch (modelIndex) {
            case TrackTableModel.COLUMN_INDEX_STATUS -> COLUMN_WIDTH_STATUS;
            case TrackTableModel.COLUMN_INDEX_TRACK_NUMBER -> COLUMN_WIDTH_TRACK_NUMBER;
            case TrackTableModel.COLUMN_INDEX_TITLE -> COLUMN_WIDTH_TITLE;
            case TrackTableModel.COLUMN_INDEX_ARTIST -> COLUMN_WIDTH_ARTIST;
            case TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST -> COLUMN_WIDTH_ALBUM_ARTIST;
            case TrackTableModel.COLUMN_INDEX_ALBUM -> COLUMN_WIDTH_ALBUM;
            case TrackTableModel.COLUMN_INDEX_DURATION -> COLUMN_WIDTH_DURATION;
            case TrackTableModel.COLUMN_INDEX_GENRE -> COLUMN_WIDTH_GENRE;
            case TrackTableModel.COLUMN_INDEX_YEAR -> COLUMN_WIDTH_YEAR;
            case TrackTableModel.COLUMN_INDEX_DISC_NUMBER -> COLUMN_WIDTH_DISC_NUMBER;
            case TrackTableModel.COLUMN_INDEX_BPM -> COLUMN_WIDTH_BPM;
            case TrackTableModel.COLUMN_INDEX_BITRATE -> COLUMN_WIDTH_BITRATE;
            case TrackTableModel.COLUMN_INDEX_DATE_ADDED -> COLUMN_WIDTH_DATE_ADDED;
            case TrackTableModel.COLUMN_INDEX_PLAY_COUNT -> COLUMN_WIDTH_PLAY_COUNT;
            case TrackTableModel.COLUMN_INDEX_RATING -> COLUMN_WIDTH_RATING;
            default -> 100;
        };
    }

    private void configureColumnRenderer(TableColumn col, int modelIndex) {
        switch (modelIndex) {
            case TrackTableModel.COLUMN_INDEX_STATUS -> col.setCellRenderer(statusRenderer);
            case TrackTableModel.COLUMN_INDEX_TRACK_NUMBER -> col.setCellRenderer(TRACK_NUMBER_RENDERER);
            case TrackTableModel.COLUMN_INDEX_TITLE -> col.setCellRenderer(new TitleCellRenderer());
            case TrackTableModel.COLUMN_INDEX_DURATION -> col.setCellRenderer(DURATION_RENDERER);
            case TrackTableModel.COLUMN_INDEX_YEAR -> col.setCellRenderer(YEAR_RENDERER);
            case TrackTableModel.COLUMN_INDEX_DISC_NUMBER -> col.setCellRenderer(DISC_NUMBER_RENDERER);
            case TrackTableModel.COLUMN_INDEX_BPM -> col.setCellRenderer(BPM_RENDERER);
            case TrackTableModel.COLUMN_INDEX_BITRATE -> col.setCellRenderer(BITRATE_RENDERER);
            case TrackTableModel.COLUMN_INDEX_DATE_ADDED -> col.setCellRenderer(DATE_ADDED_RENDERER);
            case TrackTableModel.COLUMN_INDEX_PLAY_COUNT -> col.setCellRenderer(PLAY_COUNT_RENDERER);
            case TrackTableModel.COLUMN_INDEX_RATING -> col.setCellRenderer(RATING_RENDERER);
            default -> {
                // Default left-aligned renderer for text columns (Artist, Album Artist, Album, Genre)
            }
        }
    }

    /**
     * Initializes header right-click context menu with checkboxes for toggling column visibility.
     */
    private void setupHeaderContextMenu() {
        for (int i = 0; i < TrackTableModel.COLUMN_KEYS.length; i++) {
            final int modelIndex = i;
            if (modelIndex == TrackTableModel.COLUMN_INDEX_STATUS) {
                // Status column cannot be hidden and is excluded from the header popup menu
                columnMenuItems[i] = null;
                continue;
            }
            JCheckBoxMenuItem item = new JCheckBoxMenuItem();
            if (modelIndex == TrackTableModel.COLUMN_INDEX_TITLE) {
                // Title is mandatory and cannot be hidden
                item.setEnabled(false);
                item.setSelected(true);
            } else {
                item.setSelected(DEFAULT_VISIBLE_COLUMNS.contains(modelIndex));
                item.addActionListener(e -> setColumnVisible(modelIndex, item.isSelected()));
            }
            columnMenuItems[i] = item;
            headerPopupMenu.add(item);
        }

        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleHeaderContextMenuTrigger(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleHeaderContextMenuTrigger(e);
            }
        });
    }

    private void handleHeaderContextMenuTrigger(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showColumnPopupMenu(e.getX(), e.getY());
        }
    }

    private void showColumnPopupMenu(int x, int y) {
        for (int i = 0; i < TrackTableModel.COLUMN_KEYS.length; i++) {
            if (columnMenuItems[i] == null) {
                continue;
            }
            if (i == TrackTableModel.COLUMN_INDEX_TITLE) {
                columnMenuItems[i].setSelected(true);
            } else {
                columnMenuItems[i].setSelected(isColumnVisible(i));
            }
        }
        headerPopupMenu.show(table.getTableHeader(), x, y);
    }

    /**
     * Checks if a column with the specified model index is currently visible in the table.
     *
     * @param modelIndex model column index
     * @return true if visible
     */
    public boolean isColumnVisible(int modelIndex) {
        TableColumnModel colModel = table.getColumnModel();
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            if (colModel.getColumn(i).getModelIndex() == modelIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets column visibility, adding or removing it from TableColumnModel while preserving model index order.
     *
     * @param modelIndex model column index
     * @param visible    true to show, false to hide
     */
    public void setColumnVisible(int modelIndex, boolean visible) {
        if (modelIndex < 0 || modelIndex >= allTableColumns.length) {
            return;
        }
        // Status and Title columns cannot be hidden
        if (modelIndex == TrackTableModel.COLUMN_INDEX_STATUS || (modelIndex == TrackTableModel.COLUMN_INDEX_TITLE && !visible)) {
            return;
        }

        boolean currentlyVisible = isColumnVisible(modelIndex);
        if (currentlyVisible == visible) {
            return;
        }

        TableColumn col = allTableColumns[modelIndex];
        TableColumnModel colModel = table.getColumnModel();
        if (visible) {
            colModel.addColumn(col);
            int count = colModel.getColumnCount();
            int targetViewIndex = 0;
            while (targetViewIndex < count - 1) {
                if (colModel.getColumn(targetViewIndex).getModelIndex() > modelIndex) {
                    break;
                }
                targetViewIndex++;
            }
            if (targetViewIndex == 0) {
                targetViewIndex = 1;
            }
            if (targetViewIndex < count - 1) {
                colModel.moveColumn(count - 1, targetViewIndex);
            }
        } else {
            colModel.removeColumn(col);
        }
        if (columnMenuItems != null && modelIndex < columnMenuItems.length && columnMenuItems[modelIndex] != null) {
            columnMenuItems[modelIndex].setSelected(visible);
        }
        table.getTableHeader().repaint();
    }

    /**
     * Returns list of currently visible column model indices in view order.
     */
    public List<Integer> getVisibleColumnModelIndices() {
        TableColumnModel colModel = table.getColumnModel();
        List<Integer> indices = new ArrayList<>(colModel.getColumnCount());
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            indices.add(colModel.getColumn(i).getModelIndex());
        }
        return indices;
    }

    /**
     * Exports the current column configurations including model index, column width,
     * view order, and visibility.
     *
     * @return list of column configurations
     */
    public List<UserPreferences.ColumnConfig> exportColumnConfigs() {
        List<UserPreferences.ColumnConfig> list = new ArrayList<>();
        TableColumnModel colModel = table.getColumnModel();
        Set<Integer> visibleModelIndices = new HashSet<>();

        // 1. Add currently visible columns in current view order with their current widths
        for (int viewIdx = 0; viewIdx < colModel.getColumnCount(); viewIdx++) {
            TableColumn col = colModel.getColumn(viewIdx);
            int modelIdx = col.getModelIndex();
            visibleModelIndices.add(modelIdx);
            list.add(new UserPreferences.ColumnConfig(modelIdx, col.getWidth(), true));
        }

        // 2. Add non-visible columns in model index order
        for (int modelIdx = 0; modelIdx < allTableColumns.length; modelIdx++) {
            if (!visibleModelIndices.contains(modelIdx)) {
                TableColumn col = allTableColumns[modelIdx];
                int width = col != null ? col.getWidth() : getPreferredWidthForColumn(modelIdx);
                list.add(new UserPreferences.ColumnConfig(modelIdx, width, false));
            }
        }
        return list;
    }

    /**
     * Applies column configurations restoring column visibility, view order, and column widths.
     *
     * @param configs column configurations to apply
     */
    public void applyColumnConfigs(List<UserPreferences.ColumnConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        TableColumnModel colModel = table.getColumnModel();

        // Clear active columns
        while (colModel.getColumnCount() > 0) {
            colModel.removeColumn(colModel.getColumn(0));
        }

        Set<Integer> addedModelIndices = new HashSet<>();

        // Status column is mandatory and must always be at view index 0
        TableColumn statusCol = allTableColumns[TrackTableModel.COLUMN_INDEX_STATUS];
        statusCol.setPreferredWidth(COLUMN_WIDTH_STATUS);
        statusCol.setMinWidth(COLUMN_WIDTH_STATUS);
        statusCol.setMaxWidth(COLUMN_WIDTH_STATUS);
        statusCol.setResizable(false);
        colModel.addColumn(statusCol);
        addedModelIndices.add(TrackTableModel.COLUMN_INDEX_STATUS);

        for (UserPreferences.ColumnConfig cfg : configs) {
            int modelIdx = cfg.getModelIndex();
            if (modelIdx == TrackTableModel.COLUMN_INDEX_STATUS || addedModelIndices.contains(modelIdx)) {
                continue;
            }
            if (modelIdx >= 0 && modelIdx < allTableColumns.length) {
                TableColumn col = allTableColumns[modelIdx];
                if (cfg.getWidth() > 0) {
                    col.setPreferredWidth(cfg.getWidth());
                    col.setWidth(cfg.getWidth());
                }
                if (cfg.isVisible() || modelIdx == TrackTableModel.COLUMN_INDEX_TITLE) {
                    colModel.addColumn(col);
                    addedModelIndices.add(modelIdx);
                }
            }
        }

        // Title column is mandatory: guarantee it is added
        if (!addedModelIndices.contains(TrackTableModel.COLUMN_INDEX_TITLE)) {
            TableColumn titleCol = allTableColumns[TrackTableModel.COLUMN_INDEX_TITLE];
            colModel.addColumn(titleCol);
        }

        // Synchronize checkbox menu items
        if (columnMenuItems != null) {
            for (int i = 0; i < columnMenuItems.length; i++) {
                if (columnMenuItems[i] != null) {
                    columnMenuItems[i].setSelected(isColumnVisible(i));
                }
            }
        }
        table.getTableHeader().repaint();
    }

    /**
     * Exports current table sort configuration.
     *
     * @return active sort configuration
     */
    public UserPreferences.SortConfig exportSortConfig() {
        if (rowSorter != null) {
            List<? extends RowSorter.SortKey> keys = rowSorter.getSortKeys();
            if (keys != null && !keys.isEmpty()) {
                RowSorter.SortKey key = keys.get(0);
                String order = switch (key.getSortOrder()) {
                    case ASCENDING -> "ASCENDING";
                    case DESCENDING -> "DESCENDING";
                    default -> "UNSORTED";
                };
                return new UserPreferences.SortConfig(key.getColumn(), order);
            }
        }
        return new UserPreferences.SortConfig(-1, "UNSORTED");
    }

    /**
     * Restores table sort configuration using RowSorter.
     *
     * @param sortConfig sort configuration to restore
     */
    public void applySortConfig(UserPreferences.SortConfig sortConfig) {
        if (rowSorter == null || sortConfig == null) {
            return;
        }
        int col = sortConfig.getModelIndex();
        if (col > 0 && col < tableModel.getColumnCount()) {
            SortOrder order = "DESCENDING".equalsIgnoreCase(sortConfig.getSortOrder())
                    ? SortOrder.DESCENDING
                    : ("ASCENDING".equalsIgnoreCase(sortConfig.getSortOrder())
                    ? SortOrder.ASCENDING
                    : SortOrder.UNSORTED);
            if (order != SortOrder.UNSORTED) {
                rowSorter.setSortKeys(List.of(new RowSorter.SortKey(col, order)));
            } else {
                rowSorter.setSortKeys(null);
            }
        } else {
            rowSorter.setSortKeys(null);
        }
    }

    public JPopupMenu getHeaderPopupMenu() {
        return headerPopupMenu;
    }

    public JCheckBoxMenuItem[] getColumnMenuItems() {
        return columnMenuItems;
    }

    private void setupRowContextMenu() {
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
     * Updates column header titles, header popup menu items, and row context menu items from I18nManager.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();

        // Update all table columns and checkbox menu items
        for (int i = 0; i < allTableColumns.length; i++) {
            String title = i18n.get(TrackTableModel.COLUMN_KEYS[i]);
            allTableColumns[i].setHeaderValue(title);
            if (columnMenuItems != null && i < columnMenuItems.length && columnMenuItems[i] != null) {
                columnMenuItems[i].setText(title);
            }
        }

        // Also ensure currently visible columns have header values updated
        TableColumnModel columnModel = table.getColumnModel();
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn col = columnModel.getColumn(i);
            int modelIdx = col.getModelIndex();
            if (modelIdx >= 0 && modelIdx < TrackTableModel.COLUMN_KEYS.length) {
                col.setHeaderValue(i18n.get(TrackTableModel.COLUMN_KEYS[modelIdx]));
            }
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

    /**
     * Updates the current playback status indicator in the track table.
     *
     * @param trackId ID of the track currently being played or paused, or null if stopped
     * @param state   playback state (PLAYING, PAUSED, STOPPED)
     */
    public void setPlaybackStatus(String trackId, PlaybackState state) {
        this.currentPlayingTrackId = trackId;
        this.currentPlaybackState = state != null ? state : PlaybackState.STOPPED;
        if (SwingUtilities.isEventDispatchThread()) {
            table.repaint();
        } else {
            SwingUtilities.invokeLater(table::repaint);
        }
    }

    public String getCurrentPlayingTrackId() {
        return currentPlayingTrackId;
    }

    public PlaybackState getCurrentPlaybackState() {
        return currentPlaybackState;
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

    private static class BitrateCellRenderer extends DefaultTableCellRenderer {
        public BitrateCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Number num) {
                int bitrate = num.intValue();
                if (bitrate > 0) {
                    setText(bitrate + " kbps");
                } else {
                    setText("-");
                }
            } else {
                setText("-");
            }
        }
    }

    private static class DateCellRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                .withZone(ZoneId.systemDefault());

        public DateCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Instant instant) {
                setText(FORMATTER.format(instant));
            } else {
                setText("-");
            }
        }
    }

    private static class PlayCountCellRenderer extends DefaultTableCellRenderer {
        public PlayCountCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            if (value instanceof Number num) {
                int count = num.intValue();
                setText(count >= 0 ? String.valueOf(count) : "0");
            } else {
                setText("0");
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

    private class StatusCellRenderer extends DefaultTableCellRenderer {
        private final PlaybackStatusIcon playingIcon = new PlaybackStatusIcon(PlaybackState.PLAYING);
        private final PlaybackStatusIcon pausedIcon = new PlaybackStatusIcon(PlaybackState.PAUSED);

        public StatusCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            Track track = tableModel.getTrackAt(modelRow);

            if (track != null && track.getId() != null && track.getId().equals(currentPlayingTrackId)
                    && (currentPlaybackState == PlaybackState.PLAYING || currentPlaybackState == PlaybackState.PAUSED)) {
                setText("");
                if (currentPlaybackState == PlaybackState.PLAYING) {
                    setIcon(playingIcon);
                    setToolTipText(I18nManager.getInstance().get("status.playback_playing", "Playing"));
                } else {
                    setIcon(pausedIcon);
                    setToolTipText(I18nManager.getInstance().get("status.playback_paused", "Paused"));
                }
            } else {
                setIcon(null);
                setText("");
                setToolTipText(null);
            }
            return c;
        }
    }
}
