package com.musiclibrary.ui.table;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Track;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * High-performance, virtualized TableModel for Track entities.
 * Supports efficient batch insertions, targeted row updates, and exact-type column class
 * resolution to enable natural numeric sorting via TableRowSorter without EDT blocking.
 */
public class TrackTableModel extends AbstractTableModel {

    public static final int COLUMN_INDEX_TRACK_NUMBER = 0;
    public static final int COLUMN_INDEX_TITLE = 1;
    public static final int COLUMN_INDEX_ARTIST = 2;
    public static final int COLUMN_INDEX_ALBUM = 3;
    public static final int COLUMN_INDEX_DURATION = 4;
    public static final int COLUMN_INDEX_GENRE = 5;
    public static final int COLUMN_INDEX_YEAR = 6;
    public static final int COLUMN_INDEX_RATING = 7;

    public static final String[] COLUMN_KEYS = {
            "table.column.track_number",
            "table.column.title",
            "table.column.artist",
            "table.column.album",
            "table.column.duration",
            "table.column.genre",
            "table.column.year",
            "table.column.rating"
    };

    private final List<Track> tracks;

    public TrackTableModel() {
        this(new ArrayList<>());
    }

    public TrackTableModel(List<Track> initialTracks) {
        this.tracks = new ArrayList<>();
        if (initialTracks != null && !initialTracks.isEmpty()) {
            this.tracks.addAll(initialTracks);
        }
    }

    @Override
    public int getRowCount() {
        return tracks.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_KEYS.length;
    }

    @Override
    public String getColumnName(int column) {
        if (column >= 0 && column < COLUMN_KEYS.length) {
            return I18nManager.getInstance().get(COLUMN_KEYS[column]);
        }
        return "";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case COLUMN_INDEX_TRACK_NUMBER -> Integer.class;
            case COLUMN_INDEX_TITLE -> String.class;
            case COLUMN_INDEX_ARTIST -> String.class;
            case COLUMN_INDEX_ALBUM -> String.class;
            case COLUMN_INDEX_DURATION -> Long.class;
            case COLUMN_INDEX_GENRE -> String.class;
            case COLUMN_INDEX_YEAR -> Integer.class;
            case COLUMN_INDEX_RATING -> Integer.class;
            default -> Object.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= tracks.size()) {
            return null;
        }
        Track track = tracks.get(rowIndex);
        if (track == null) {
            return null;
        }

        return switch (columnIndex) {
            case COLUMN_INDEX_TRACK_NUMBER -> track.getTrackNumber();
            case COLUMN_INDEX_TITLE -> track.getTitle();
            case COLUMN_INDEX_ARTIST -> track.getArtist();
            case COLUMN_INDEX_ALBUM -> track.getAlbum();
            case COLUMN_INDEX_DURATION -> track.getDurationSeconds();
            case COLUMN_INDEX_GENRE -> track.getGenre();
            case COLUMN_INDEX_YEAR -> track.getYear();
            case COLUMN_INDEX_RATING -> track.getRating();
            default -> null;
        };
    }

    /**
     * Replaces the entire track list and notifies listeners of full data reload.
     *
     * @param newTracks new track collection
     */
    public void setTracks(List<Track> newTracks) {
        tracks.clear();
        if (newTracks != null && !newTracks.isEmpty()) {
            tracks.addAll(newTracks);
        }
        fireTableDataChanged();
    }

    /**
     * Appends a single track using targeted fireTableRowsInserted notification.
     *
     * @param track track to add
     */
    public void addTrack(Track track) {
        if (track == null) {
            return;
        }
        int index = tracks.size();
        tracks.add(track);
        fireTableRowsInserted(index, index);
    }

    /**
     * Appends a batch of tracks using a single range-based fireTableRowsInserted notification,
     * preventing UI stutter during high-throughput file imports.
     *
     * @param batch collection of tracks to insert
     */
    public void addTracks(List<Track> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        int from = tracks.size();
        tracks.addAll(batch);
        int to = tracks.size() - 1;
        fireTableRowsInserted(from, to);
    }

    /**
     * Updates an existing track matching by track ID and triggers a single-row repaint.
     *
     * @param track updated track entity
     * @return true if track was found and updated, false otherwise
     */
    public boolean updateTrack(Track track) {
        if (track == null || track.getId() == null) {
            return false;
        }
        String id = track.getId();
        int row = indexOfTrack(id);
        if (row != -1) {
            tracks.set(row, track);
            fireTableRowsUpdated(row, row);
            return true;
        }
        return false;
    }

    /**
     * Removes the track at the specified model row index.
     *
     * @param rowIndex row index to remove
     * @return removed track, or null if out of range
     */
    public Track removeTrack(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= tracks.size()) {
            return null;
        }
        Track removed = tracks.remove(rowIndex);
        fireTableRowsDeleted(rowIndex, rowIndex);
        return removed;
    }

    /**
     * Removes a track by its unique track ID.
     *
     * @param trackId track ID to remove
     * @return removed Track or null if not found
     */
    public Track removeTrackById(String trackId) {
        int row = indexOfTrack(trackId);
        if (row != -1) {
            return removeTrack(row);
        }
        return null;
    }

    /**
     * Clears all tracks from the model.
     */
    public void clear() {
        int size = tracks.size();
        if (size > 0) {
            tracks.clear();
            fireTableRowsDeleted(0, size - 1);
        }
    }

    /**
     * Returns the Track instance at the specified model row index.
     *
     * @param rowIndex model row index
     * @return Track or null if index is invalid
     */
    public Track getTrackAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < tracks.size()) {
            return tracks.get(rowIndex);
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of all tracks in the model.
     *
     * @return list of tracks
     */
    public List<Track> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    /**
     * Finds the model row index corresponding to the given track ID.
     *
     * @param trackId track ID to locate
     * @return row index, or -1 if not found
     */
    public int indexOfTrack(String trackId) {
        if (trackId == null) {
            return -1;
        }
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            if (t != null && Objects.equals(t.getId(), trackId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Notifies table of structure change (e.g. on language change).
     */
    public void updateLanguage() {
        fireTableStructureChanged();
    }
}
