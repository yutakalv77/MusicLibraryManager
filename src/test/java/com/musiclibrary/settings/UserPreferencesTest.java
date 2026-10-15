package com.musiclibrary.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferencesTest {

    @Test
    @DisplayName("UserPreferences initializes with expected default settings")
    void testDefaultValues() {
        UserPreferences prefs = new UserPreferences();

        assertThat(prefs.getWindowX()).isEqualTo(-1);
        assertThat(prefs.getWindowY()).isEqualTo(-1);
        assertThat(prefs.getWindowWidth()).isEqualTo(1100);
        assertThat(prefs.getWindowHeight()).isEqualTo(720);
        assertThat(prefs.isWindowMaximized()).isFalse();

        assertThat(prefs.getLanguage()).isEqualTo("SYSTEM");
        assertThat(prefs.getTheme()).isEqualTo("DARK");
        assertThat(prefs.getVolumePercent()).isEqualTo(80);

        List<UserPreferences.ColumnConfig> cols = prefs.getColumns();
        assertThat(cols).hasSize(15);
        assertThat(cols.get(0).getModelIndex()).isEqualTo(0);
        assertThat(cols.get(0).getWidth()).isEqualTo(30);
        assertThat(cols.get(0).isVisible()).isTrue();

        UserPreferences.SortConfig sort = prefs.getSort();
        assertThat(sort.getModelIndex()).isEqualTo(-1);
        assertThat(sort.getSortOrder()).isEqualTo("UNSORTED");
    }

    @Test
    @DisplayName("Setters enforce boundary constraints and null safety")
    void testSettersBoundaryConstraints() {
        UserPreferences prefs = new UserPreferences();

        // Volume clamping
        prefs.setVolumePercent(-10);
        assertThat(prefs.getVolumePercent()).isEqualTo(0);
        prefs.setVolumePercent(150);
        assertThat(prefs.getVolumePercent()).isEqualTo(100);
        prefs.setVolumePercent(65);
        assertThat(prefs.getVolumePercent()).isEqualTo(65);

        // Window size minimums
        prefs.setWindowWidth(500);
        assertThat(prefs.getWindowWidth()).isEqualTo(800);
        prefs.setWindowHeight(300);
        assertThat(prefs.getWindowHeight()).isEqualTo(520);

        // Language & Theme null safety
        prefs.setLanguage(null);
        assertThat(prefs.getLanguage()).isEqualTo("SYSTEM");
        prefs.setLanguage("   ");
        assertThat(prefs.getLanguage()).isEqualTo("SYSTEM");
        prefs.setLanguage("JAPANESE");
        assertThat(prefs.getLanguage()).isEqualTo("JAPANESE");

        prefs.setTheme(null);
        assertThat(prefs.getTheme()).isEqualTo("DARK");
        prefs.setTheme("   ");
        assertThat(prefs.getTheme()).isEqualTo("DARK");
        prefs.setTheme("LIGHT");
        assertThat(prefs.getTheme()).isEqualTo("LIGHT");

        // Columns & Sort null safety
        prefs.setColumns(null);
        assertThat(prefs.getColumns()).isNotNull();

        prefs.setSort(null);
        assertThat(prefs.getSort()).isNotNull();
        assertThat(prefs.getSort().getModelIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("ColumnConfig and SortConfig equals, hashCode, and toString contracts")
    void testConfigsContracts() {
        UserPreferences.ColumnConfig col1 = new UserPreferences.ColumnConfig(1, 200, true);
        UserPreferences.ColumnConfig col2 = new UserPreferences.ColumnConfig(1, 200, true);
        UserPreferences.ColumnConfig col3 = new UserPreferences.ColumnConfig(2, 200, true);

        assertThat(col1).isEqualTo(col2);
        assertThat(col1.hashCode()).isEqualTo(col2.hashCode());
        assertThat(col1).isNotEqualTo(col3);
        assertThat(col1.toString()).contains("modelIndex=1", "width=200", "visible=true");

        UserPreferences.SortConfig sort1 = new UserPreferences.SortConfig(1, "ASCENDING");
        UserPreferences.SortConfig sort2 = new UserPreferences.SortConfig(1, "ASCENDING");
        UserPreferences.SortConfig sort3 = new UserPreferences.SortConfig(1, "DESCENDING");

        assertThat(sort1).isEqualTo(sort2);
        assertThat(sort1.hashCode()).isEqualTo(sort2.hashCode());
        assertThat(sort1).isNotEqualTo(sort3);
        assertThat(sort1.toString()).contains("modelIndex=1", "sortOrder='ASCENDING'");
    }

    @Test
    @DisplayName("migrateLegacyColumns correctly shifts legacy 14 columns and prepends Status column")
    void testLegacy14ColumnsMigration() {
        UserPreferences prefs = new UserPreferences();
        // Construct legacy 14 columns (indices 0..13)
        List<UserPreferences.ColumnConfig> legacyCols = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) {
            legacyCols.add(new UserPreferences.ColumnConfig(i, 100 + i, i % 2 == 0));
        }
        prefs.setSort(new UserPreferences.SortConfig(2, "ASCENDING"));
        prefs.setColumns(legacyCols);

        List<UserPreferences.ColumnConfig> migrated = prefs.getColumns();
        assertThat(migrated).hasSize(15);
        assertThat(migrated.get(0).getModelIndex()).isEqualTo(0);
        assertThat(migrated.get(0).getWidth()).isEqualTo(30);
        assertThat(migrated.get(0).isVisible()).isTrue();

        // Previous 0 (Track#) is now index 1
        assertThat(migrated.get(1).getModelIndex()).isEqualTo(1);
        assertThat(migrated.get(1).getWidth()).isEqualTo(100);

        // Sort index migrated from 2 to 3
        assertThat(prefs.getSort().getModelIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("migrateLegacyColumns is idempotent and does not shift columns if already migrated to 15 columns")
    void testLegacy14ColumnsMigrationIdempotence() {
        UserPreferences prefs = new UserPreferences();
        // Already migrated with 15 columns (indices 0..14)
        List<UserPreferences.ColumnConfig> fifteenCols = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            fifteenCols.add(new UserPreferences.ColumnConfig(i, 80 + i, true));
        }
        prefs.setSort(new UserPreferences.SortConfig(2, "ASCENDING"));
        prefs.setColumns(fifteenCols);

        // Calling migrateLegacyColumns again should not change anything
        prefs.migrateLegacyColumns();

        List<UserPreferences.ColumnConfig> cols = prefs.getColumns();
        assertThat(cols).hasSize(15);
        assertThat(cols.get(0).getModelIndex()).isEqualTo(0);
        assertThat(cols.get(14).getModelIndex()).isEqualTo(14);
        assertThat(prefs.getSort().getModelIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("migrateLegacyColumns resets to default columns when columns list is empty")
    void testLegacyMigrationWithEmptyColumns() {
        UserPreferences prefs = new UserPreferences();
        prefs.setColumns(new java.util.ArrayList<>());

        prefs.migrateLegacyColumns();

        List<UserPreferences.ColumnConfig> cols = prefs.getColumns();
        assertThat(cols).hasSize(15);
        assertThat(cols.get(0).getModelIndex()).isEqualTo(0);
        assertThat(cols.get(0).getWidth()).isEqualTo(30);
    }
}
