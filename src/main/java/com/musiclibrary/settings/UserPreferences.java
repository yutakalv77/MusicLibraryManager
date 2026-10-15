package com.musiclibrary.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serializable data model for storing persistent user preferences including
 * window bounds/state, theme, language, volume, and track table layout.
 */
public class UserPreferences {

    // Window settings
    private int windowX = -1;
    private int windowY = -1;
    private int windowWidth = 1100;
    private int windowHeight = 720;
    private boolean windowMaximized = false;

    // Language & Theme settings
    private String language = "SYSTEM";
    private String theme = "DARK";

    // Playback settings
    private int volumePercent = 80;

    // Table Column & Sort settings
    private List<ColumnConfig> columns = new ArrayList<>();
    private SortConfig sort = new SortConfig();

    public UserPreferences() {
        initDefaultColumns();
    }

    /**
     * Initializes default columns with model indices 0-14, standard widths,
     * and default visibility matching TrackTablePanel defaults.
     */
    public void initDefaultColumns() {
        this.columns = new ArrayList<>();
        // Default visible: Status(0), Track#(1), Title(2), Artist(3), Album(5), Duration(6), Genre(7), Year(8), Rating(14)
        columns.add(new ColumnConfig(0, 30, true));    // Status
        columns.add(new ColumnConfig(1, 80, true));    // Track#
        columns.add(new ColumnConfig(2, 240, true));   // Title
        columns.add(new ColumnConfig(3, 160, true));   // Artist
        columns.add(new ColumnConfig(4, 160, false));  // Album Artist
        columns.add(new ColumnConfig(5, 180, true));   // Album
        columns.add(new ColumnConfig(6, 75, true));    // Duration
        columns.add(new ColumnConfig(7, 110, true));   // Genre
        columns.add(new ColumnConfig(8, 65, true));    // Year
        columns.add(new ColumnConfig(9, 55, false));   // Disc#
        columns.add(new ColumnConfig(10, 60, false));  // BPM
        columns.add(new ColumnConfig(11, 75, false));  // Bitrate
        columns.add(new ColumnConfig(12, 130, false)); // Date Added
        columns.add(new ColumnConfig(13, 70, false));  // Plays
        columns.add(new ColumnConfig(14, 85, true));   // Rating
    }

    /**
     * Migrates legacy 14-column preferences to 15 columns by shifting model indices by +1
     * and inserting the permanent Status column at model index 0.
     */
    public void migrateLegacyColumns() {
        if (columns == null || columns.isEmpty()) {
            initDefaultColumns();
            return;
        }
        if (columns.size() == 14) {
            boolean hasLegacyIndices = columns.stream().noneMatch(c -> c.getModelIndex() == 14);
            if (hasLegacyIndices) {
                for (ColumnConfig col : columns) {
                    col.setModelIndex(col.getModelIndex() + 1);
                }
                columns.add(0, new ColumnConfig(0, 30, true));
                if (sort != null && sort.getModelIndex() >= 0) {
                    sort.setModelIndex(sort.getModelIndex() + 1);
                }
            }
        }
    }

    // --- Getters & Setters ---

    public int getWindowX() {
        return windowX;
    }

    public void setWindowX(int windowX) {
        this.windowX = windowX;
    }

    public int getWindowY() {
        return windowY;
    }

    public void setWindowY(int windowY) {
        this.windowY = windowY;
    }

    public int getWindowWidth() {
        return Math.max(800, windowWidth);
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = Math.max(800, windowWidth);
    }

    public int getWindowHeight() {
        return Math.max(520, windowHeight);
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = Math.max(520, windowHeight);
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }

    public String getLanguage() {
        return language != null ? language : "SYSTEM";
    }

    public void setLanguage(String language) {
        this.language = (language != null && !language.isBlank()) ? language : "SYSTEM";
    }

    public String getTheme() {
        return theme != null ? theme : "DARK";
    }

    public void setTheme(String theme) {
        this.theme = (theme != null && !theme.isBlank()) ? theme : "DARK";
    }

    public int getVolumePercent() {
        return Math.max(0, Math.min(100, volumePercent));
    }

    public void setVolumePercent(int volumePercent) {
        this.volumePercent = Math.max(0, Math.min(100, volumePercent));
    }

    public List<ColumnConfig> getColumns() {
        if (columns == null || columns.isEmpty()) {
            initDefaultColumns();
        } else {
            migrateLegacyColumns();
        }
        return columns;
    }

    public void setColumns(List<ColumnConfig> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
        migrateLegacyColumns();
    }

    public SortConfig getSort() {
        if (sort == null) {
            sort = new SortConfig();
        }
        return sort;
    }

    public void setSort(SortConfig sort) {
        this.sort = sort != null ? sort : new SortConfig();
    }

    // --- Nested Configuration Classes ---

    /**
     * Column configuration representing view index, model index, width, and visibility.
     */
    public static class ColumnConfig {
        private int modelIndex;
        private int width;
        private boolean visible;

        public ColumnConfig() {
        }

        public ColumnConfig(int modelIndex, int width, boolean visible) {
            this.modelIndex = modelIndex;
            this.width = width;
            this.visible = visible;
        }

        public int getModelIndex() {
            return modelIndex;
        }

        public void setModelIndex(int modelIndex) {
            this.modelIndex = modelIndex;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ColumnConfig that = (ColumnConfig) o;
            return modelIndex == that.modelIndex && width == that.width && visible == that.visible;
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelIndex, width, visible);
        }

        @Override
        public String toString() {
            return "ColumnConfig{" +
                    "modelIndex=" + modelIndex +
                    ", width=" + width +
                    ", visible=" + visible +
                    '}';
        }
    }

    /**
     * Sort configuration representing sort column index and sort order.
     */
    public static class SortConfig {
        private int modelIndex = -1;
        private String sortOrder = "UNSORTED";

        public SortConfig() {
        }

        public SortConfig(int modelIndex, String sortOrder) {
            this.modelIndex = modelIndex;
            this.sortOrder = sortOrder != null ? sortOrder : "UNSORTED";
        }

        public int getModelIndex() {
            return modelIndex;
        }

        public void setModelIndex(int modelIndex) {
            this.modelIndex = modelIndex;
        }

        public String getSortOrder() {
            return sortOrder != null ? sortOrder : "UNSORTED";
        }

        public void setSortOrder(String sortOrder) {
            this.sortOrder = sortOrder != null ? sortOrder : "UNSORTED";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SortConfig that = (SortConfig) o;
            return modelIndex == that.modelIndex && Objects.equals(sortOrder, that.sortOrder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelIndex, sortOrder);
        }

        @Override
        public String toString() {
            return "SortConfig{" +
                    "modelIndex=" + modelIndex +
                    ", sortOrder='" + sortOrder + '\'' +
                    '}';
        }
    }
}
