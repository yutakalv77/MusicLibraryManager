package com.musiclibrary.controller;

import com.musiclibrary.model.Track;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.ui.panel.TrackTablePanel;
import com.musiclibrary.ui.table.TrackTableModel;

import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * High-performance search controller coordinating incremental query inputs from
 * the search bar, fast in-memory indexing via {@link FastSearchIndex}, and zero-allocation
 * table filtering via {@link TableRowSorter}.
 */
public class SearchController {

    private final FastSearchIndex searchIndex;
    private final JTextField searchField;
    private final TrackTablePanel trackTablePanel;
    private final TableRowSorter<TrackTableModel> rowSorter;

    private final DocumentListener documentListener;
    private final KeyAdapter keyListener;
    private final List<Consumer<Integer>> matchCountListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> queryListeners = new CopyOnWriteArrayList<>();

    private volatile boolean disposed = false;

    public SearchController(FastSearchIndex searchIndex, JTextField searchField, TrackTablePanel trackTablePanel) {
        this.searchIndex = Objects.requireNonNull(searchIndex, "searchIndex must not be null");
        this.searchField = Objects.requireNonNull(searchField, "searchField must not be null");
        this.trackTablePanel = Objects.requireNonNull(trackTablePanel, "trackTablePanel must not be null");
        this.rowSorter = Objects.requireNonNull(trackTablePanel.getRowSorter(), "rowSorter must not be null");

        this.documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        };

        this.keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    clearSearch();
                    e.consume();
                }
            }
        };

        this.searchField.getDocument().addDocumentListener(this.documentListener);
        this.searchField.addKeyListener(this.keyListener);
    }

    /**
     * Executes fast query filtering on the table model and notifies match count listeners.
     */
    public void applyFilter() {
        if (disposed) {
            return;
        }

        String query = searchField.getText();
        notifyQueryListeners(query);
        if (query == null || query.isBlank()) {
            rowSorter.setRowFilter(null);
            int totalCount = trackTablePanel.getTableModel().getRowCount();
            notifyMatchCount(totalCount);
            return;
        }

        Set<String> matchedIds = searchIndex.search(query);
        RowFilter<TrackTableModel, Integer> filter = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TrackTableModel, ? extends Integer> entry) {
                int modelRow = entry.getIdentifier();
                Track track = entry.getModel().getTrackAt(modelRow);
                return track != null && matchedIds.contains(track.getId());
            }
        };

        rowSorter.setRowFilter(filter);
        int viewCount = rowSorter.getViewRowCount();
        notifyMatchCount(viewCount);
    }

    /**
     * Clears current search input and restores full table view.
     */
    public void clearSearch() {
        if (!disposed && !searchField.getText().isEmpty()) {
            searchField.setText("");
        }
    }

    /**
     * Checks if there is an active non-blank search query.
     *
     * @return true if an active search query is entered, false otherwise
     */
    public boolean hasActiveFilter() {
        if (disposed) {
            return false;
        }
        String query = searchField.getText();
        return query != null && !query.isBlank();
    }

    /**
     * Adds a listener notified with the number of matched tracks whenever search changes.
     *
     * @param listener match count listener
     */
    public void addMatchCountListener(Consumer<Integer> listener) {
        if (listener != null && !matchCountListeners.contains(listener)) {
            matchCountListeners.add(listener);
        }
    }

    /**
     * Removes a registered match count listener.
     *
     * @param listener match count listener
     */
    public void removeMatchCountListener(Consumer<Integer> listener) {
        if (listener != null) {
            matchCountListeners.remove(listener);
        }
    }

    private void notifyMatchCount(int count) {
        for (Consumer<Integer> listener : matchCountListeners) {
            try {
                listener.accept(count);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Registers a listener to receive query string changes.
     *
     * @param listener consumer receiving current query string
     */
    public void addQueryListener(Consumer<String> listener) {
        if (listener != null) {
            queryListeners.add(listener);
        }
    }

    public void removeQueryListener(Consumer<String> listener) {
        queryListeners.remove(listener);
    }

    private void notifyQueryListeners(String query) {
        for (Consumer<String> listener : queryListeners) {
            try {
                listener.accept(query);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Disposes listeners and releases UI references to prevent memory leaks.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        searchField.getDocument().removeDocumentListener(documentListener);
        searchField.removeKeyListener(keyListener);
        matchCountListeners.clear();
        queryListeners.clear();
    }

    public FastSearchIndex getSearchIndex() {
        return searchIndex;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public TrackTablePanel getTrackTablePanel() {
        return trackTablePanel;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
