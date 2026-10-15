package com.musiclibrary.settings;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Library;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.MainFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.RowSorter;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsManagerTest {

    @TempDir
    Path tempDir;

    private Path settingsPath;
    private SettingsManager settingsManager;

    @BeforeEach
    void setUp() {
        settingsPath = tempDir.resolve("subfolder").resolve("settings.json");
        settingsManager = new SettingsManager(settingsPath);
    }

    @AfterEach
    void tearDown() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
    }

    @Test
    @DisplayName("load returns defaults when file does not exist")
    void testLoadNonExistentFileReturnsDefaults() {
        settingsManager.load();
        UserPreferences prefs = settingsManager.getPreferences();

        assertThat(prefs).isNotNull();
        assertThat(prefs.getVolumePercent()).isEqualTo(80);
        assertThat(prefs.getLanguage()).isEqualTo("SYSTEM");
        assertThat(prefs.getTheme()).isEqualTo("DARK");
    }

    @Test
    @DisplayName("save and load roundtrip persists all modified user preferences")
    void testSaveAndLoadRoundtrip() {
        UserPreferences prefs = new UserPreferences();
        prefs.setWindowX(150);
        prefs.setWindowY(250);
        prefs.setWindowWidth(1280);
        prefs.setWindowHeight(800);
        prefs.setWindowMaximized(true);
        prefs.setLanguage("JAPANESE");
        prefs.setTheme("LIGHT");
        prefs.setVolumePercent(42);

        List<UserPreferences.ColumnConfig> customCols = List.of(
                new UserPreferences.ColumnConfig(1, 300, true),
                new UserPreferences.ColumnConfig(2, 200, true),
                new UserPreferences.ColumnConfig(0, 50, false)
        );
        prefs.setColumns(customCols);
        prefs.setSort(new UserPreferences.SortConfig(2, "DESCENDING"));

        settingsManager.setPreferences(prefs);
        settingsManager.save();

        assertThat(Files.exists(settingsPath)).isTrue();

        // New manager loading the same file
        SettingsManager reloadedManager = new SettingsManager(settingsPath);
        reloadedManager.load();
        UserPreferences reloaded = reloadedManager.getPreferences();

        assertThat(reloaded.getWindowX()).isEqualTo(150);
        assertThat(reloaded.getWindowY()).isEqualTo(250);
        assertThat(reloaded.getWindowWidth()).isEqualTo(1280);
        assertThat(reloaded.getWindowHeight()).isEqualTo(800);
        assertThat(reloaded.isWindowMaximized()).isTrue();
        assertThat(reloaded.getLanguage()).isEqualTo("JAPANESE");
        assertThat(reloaded.getTheme()).isEqualTo("LIGHT");
        assertThat(reloaded.getVolumePercent()).isEqualTo(42);

        assertThat(reloaded.getColumns()).hasSize(3);
        assertThat(reloaded.getColumns().get(0)).isEqualTo(new UserPreferences.ColumnConfig(1, 300, true));
        assertThat(reloaded.getSort().getModelIndex()).isEqualTo(2);
        assertThat(reloaded.getSort().getSortOrder()).isEqualTo("DESCENDING");
    }

    @Test
    @DisplayName("load recovers gracefully from corrupted JSON file")
    void testLoadCorruptedFileFallsBackToDefaults() throws IOException {
        Files.createDirectories(settingsPath.getParent());
        Files.writeString(settingsPath, "{ corrupted json syntax : [[");

        settingsManager.load();
        UserPreferences prefs = settingsManager.getPreferences();

        assertThat(prefs).isNotNull();
        assertThat(prefs.getVolumePercent()).isEqualTo(80);
        assertThat(prefs.getLanguage()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("applyThemeAndLanguageEarly applies configured theme and locale")
    void testApplyThemeAndLanguageEarly() {
        UserPreferences prefs = new UserPreferences();
        prefs.setTheme("LIGHT");
        prefs.setLanguage("JAPANESE");
        settingsManager.setPreferences(prefs);

        settingsManager.applyThemeAndLanguageEarly();

        assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
        assertThat(I18nManager.getInstance().getCurrentLocale()).isEqualTo(AppLocale.JAPANESE);
    }

    @Test
    @DisplayName("applyTo and saveFrom synchronize MainFrame state correctly")
    void testApplyToAndSaveFromMainFrame() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        UserPreferences initialPrefs = new UserPreferences();
        initialPrefs.setWindowX(100);
        initialPrefs.setWindowY(100);
        initialPrefs.setWindowWidth(1000);
        initialPrefs.setWindowHeight(650);
        initialPrefs.setWindowMaximized(false);
        initialPrefs.setVolumePercent(35);
        initialPrefs.setTheme("DARK");
        initialPrefs.setLanguage("ENGLISH");

        settingsManager.setPreferences(initialPrefs);

        MainFrame frame = new MainFrame(new Library(), null);
        try {
            settingsManager.applyTo(frame);

            // Assert applied volume
            assertThat(frame.getPlayerController().getVolumePercent()).isEqualTo(35);
            assertThat(frame.getControlBarPanel().getVolumeSlider().getValue()).isEqualTo(35);

            // Assert applied dimensions
            assertThat(frame.getWidth()).isEqualTo(1000);
            assertThat(frame.getHeight()).isEqualTo(650);

            // Mutate frame state
            frame.getPlayerController().setVolumePercent(90);
            frame.setSize(new Dimension(1150, 750));
            frame.setLocation(new Point(120, 140));

            // Save from frame
            settingsManager.saveFrom(frame);

            UserPreferences saved = settingsManager.getPreferences();
            assertThat(saved.getVolumePercent()).isEqualTo(90);
            assertThat(saved.getWindowWidth()).isEqualTo(1150);
            assertThat(saved.getWindowHeight()).isEqualTo(750);
            assertThat(saved.getWindowX()).isEqualTo(120);
            assertThat(saved.getWindowY()).isEqualTo(140);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("TrackTablePanel export and apply column and sort configs")
    void testTrackTablePanelColumnAndSortPersistence() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        MainFrame frame = new MainFrame(new Library(), null);
        try {
            var tablePanel = frame.getTrackTablePanel();

            // Export initial configs
            List<UserPreferences.ColumnConfig> exportedCols = tablePanel.exportColumnConfigs();
            assertThat(exportedCols).isNotEmpty();

            UserPreferences.SortConfig exportedSort = tablePanel.exportSortConfig();
            assertThat(exportedSort).isNotNull();

            // Apply custom column configs (reordering: Title(2), Artist(3), Genre(7))
            List<UserPreferences.ColumnConfig> customConfigs = List.of(
                    new UserPreferences.ColumnConfig(2, 280, true), // Title
                    new UserPreferences.ColumnConfig(3, 190, true), // Artist
                    new UserPreferences.ColumnConfig(7, 120, true), // Genre
                    new UserPreferences.ColumnConfig(1, 45, false)  // Track# hidden
            );
            tablePanel.applyColumnConfigs(customConfigs);

            List<Integer> visibleIndices = tablePanel.getVisibleColumnModelIndices();
            assertThat(visibleIndices).containsExactly(0, 2, 3, 7);
            assertThat(tablePanel.isColumnVisible(0)).isTrue();
            assertThat(tablePanel.isColumnVisible(2)).isTrue();
            assertThat(tablePanel.isColumnVisible(3)).isTrue();
            assertThat(tablePanel.isColumnVisible(7)).isTrue();
            assertThat(tablePanel.isColumnVisible(1)).isFalse();

            // Apply sort config
            UserPreferences.SortConfig newSort = new UserPreferences.SortConfig(2, "DESCENDING");
            tablePanel.applySortConfig(newSort);

            UserPreferences.SortConfig reExportedSort = tablePanel.exportSortConfig();
            assertThat(reExportedSort.getModelIndex()).isEqualTo(2);
            assertThat(reExportedSort.getSortOrder()).isEqualTo("DESCENDING");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Window bounds out of screen coordinates fallback to center position")
    void testWindowBoundsOutOfBoundsClampsToCenter() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        // Test 1: Negative coordinates
        UserPreferences prefsNeg = new UserPreferences();
        prefsNeg.setWindowX(-500);
        prefsNeg.setWindowY(-500);
        prefsNeg.setWindowWidth(1000);
        prefsNeg.setWindowHeight(700);
        settingsManager.setPreferences(prefsNeg);

        MainFrame frame = new MainFrame(new Library(), null);
        try {
            settingsManager.applyTo(frame);
            // Window must not be positioned at -500, -500
            assertThat(frame.getX()).isGreaterThanOrEqualTo(0);
            assertThat(frame.getY()).isGreaterThanOrEqualTo(0);

            // Test 2: Extreme positive coordinates (far beyond any screen)
            UserPreferences prefsHuge = new UserPreferences();
            prefsHuge.setWindowX(99999);
            prefsHuge.setWindowY(99999);
            prefsHuge.setWindowWidth(1000);
            prefsHuge.setWindowHeight(700);
            settingsManager.setPreferences(prefsHuge);

            settingsManager.applyTo(frame);
            // Window must fall back and not be at 99999
            assertThat(frame.getX()).isLessThan(99999);
            assertThat(frame.getY()).isLessThan(99999);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Corrupted and empty JSON files safely fallback to default preferences without throwing")
    void testEmptyAndInvalidJsonFallback() throws IOException {
        Files.createDirectories(settingsPath.getParent());

        // 1. Empty file (0 bytes)
        Files.writeString(settingsPath, "");
        settingsManager.load();
        assertThat(settingsManager.getPreferences()).isNotNull();
        assertThat(settingsManager.getPreferences().getVolumePercent()).isEqualTo(80);

        // 2. Whitespace only
        Files.writeString(settingsPath, "   \n\t  ");
        settingsManager.load();
        assertThat(settingsManager.getPreferences()).isNotNull();
        assertThat(settingsManager.getPreferences().getVolumePercent()).isEqualTo(80);

        // 3. Array instead of object
        Files.writeString(settingsPath, "[1, 2, 3]");
        settingsManager.load();
        assertThat(settingsManager.getPreferences()).isNotNull();
        assertThat(settingsManager.getPreferences().getVolumePercent()).isEqualTo(80);
    }

    @Test
    @DisplayName("Volume out-of-range in JSON is clamped on deserialization")
    void testDeserializedVolumeClampedToRange() throws IOException {
        Files.createDirectories(settingsPath.getParent());

        // Negative volume
        Files.writeString(settingsPath, "{\"volumePercent\": -30}");
        settingsManager.load();
        assertThat(settingsManager.getPreferences().getVolumePercent()).isEqualTo(0);

        // Over 100 volume
        Files.writeString(settingsPath, "{\"volumePercent\": 180}");
        settingsManager.load();
        assertThat(settingsManager.getPreferences().getVolumePercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("Mandatory title column is automatically made visible even if configured false or omitted")
    void testTitleColumnMandatoryVisibility() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        MainFrame frame = new MainFrame(new Library(), null);
        try {
            var tablePanel = frame.getTrackTablePanel();

            // 1. Configured false: Title column (modelIndex 2) visible=false
            List<UserPreferences.ColumnConfig> configsWithTitleHidden = List.of(
                    new UserPreferences.ColumnConfig(0, 30, true),
                    new UserPreferences.ColumnConfig(1, 50, true),
                    new UserPreferences.ColumnConfig(2, 200, false), // title explicitly false
                    new UserPreferences.ColumnConfig(3, 150, true)
            );
            tablePanel.applyColumnConfigs(configsWithTitleHidden);

            // Title column MUST be forced to visible
            assertThat(tablePanel.isColumnVisible(2)).isTrue();
            assertThat(tablePanel.getVisibleColumnModelIndices()).contains(2);

            // 2. Omitted completely: Title column not present in configs list
            List<UserPreferences.ColumnConfig> configsWithoutTitle = List.of(
                    new UserPreferences.ColumnConfig(0, 30, true),
                    new UserPreferences.ColumnConfig(1, 50, true),
                    new UserPreferences.ColumnConfig(3, 150, true)
            );
            tablePanel.applyColumnConfigs(configsWithoutTitle);

            // Title column MUST be added automatically
            assertThat(tablePanel.isColumnVisible(2)).isTrue();
            assertThat(tablePanel.getVisibleColumnModelIndices()).contains(2);
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("Table column drag reordering and both ascending and descending sort roundtrip accurately")
    void testColumnReorderingAndSortDirectionRoundtrip() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        MainFrame frame = new MainFrame(new Library(), null);
        try {
            var tablePanel = frame.getTrackTablePanel();
            var table = tablePanel.getTable();

            // Initial: move column 1 to index 3
            table.moveColumn(1, 3);

            // Export configs
            List<UserPreferences.ColumnConfig> exported = tablePanel.exportColumnConfigs();

            // Apply to another table state
            tablePanel.applyColumnConfigs(exported);

            // Verify order in table column model matches exported order
            List<Integer> expectedVisibleModelIndices = tablePanel.getVisibleColumnModelIndices();
            for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
                int modelIdx = table.getColumnModel().getColumn(i).getModelIndex();
                assertThat(modelIdx).isEqualTo(expectedVisibleModelIndices.get(i));
            }

            // Test Ascending sort
            tablePanel.applySortConfig(new UserPreferences.SortConfig(2, "ASCENDING"));
            UserPreferences.SortConfig ascExported = tablePanel.exportSortConfig();
            assertThat(ascExported.getModelIndex()).isEqualTo(2);
            assertThat(ascExported.getSortOrder()).isEqualTo("ASCENDING");

            // Test Descending sort
            tablePanel.applySortConfig(new UserPreferences.SortConfig(2, "DESCENDING"));
            UserPreferences.SortConfig descExported = tablePanel.exportSortConfig();
            assertThat(descExported.getModelIndex()).isEqualTo(2);
            assertThat(descExported.getSortOrder()).isEqualTo("DESCENDING");

            // Test Unsorted
            tablePanel.applySortConfig(new UserPreferences.SortConfig(2, "UNSORTED"));
            UserPreferences.SortConfig unsortedExported = tablePanel.exportSortConfig();
            assertThat(unsortedExported.getModelIndex()).isEqualTo(-1);
            assertThat(unsortedExported.getSortOrder()).isEqualTo("UNSORTED");
        } finally {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("load automatically migrates legacy 14-column settings.json to 15 columns on disk load")
    void testLegacy14ColumnJsonMigrationOnLoad() throws IOException {
        // Construct legacy 14-column JSON representation without Status column (indices 0..13)
        // Legacy column 0 = Track#, column 1 = Title, column 2 = Artist, etc.
        String legacyJson = """
                {
                  "windowX": 100,
                  "windowY": 100,
                  "windowWidth": 1200,
                  "windowHeight": 800,
                  "windowMaximized": false,
                  "language": "ENGLISH",
                  "theme": "DARK",
                  "volumePercent": 75,
                  "columns": [
                    { "modelIndex": 0, "width": 50, "visible": true },
                    { "modelIndex": 1, "width": 250, "visible": true },
                    { "modelIndex": 2, "width": 180, "visible": true },
                    { "modelIndex": 3, "width": 180, "visible": false },
                    { "modelIndex": 4, "width": 180, "visible": true },
                    { "modelIndex": 5, "width": 70, "visible": true },
                    { "modelIndex": 6, "width": 100, "visible": true },
                    { "modelIndex": 7, "width": 65, "visible": true },
                    { "modelIndex": 8, "width": 55, "visible": false },
                    { "modelIndex": 9, "width": 60, "visible": false },
                    { "modelIndex": 10, "width": 75, "visible": false },
                    { "modelIndex": 11, "width": 130, "visible": false },
                    { "modelIndex": 12, "width": 70, "visible": false },
                    { "modelIndex": 13, "width": 85, "visible": true }
                  ],
                  "sort": {
                    "modelIndex": 1,
                    "sortOrder": "ASCENDING"
                  }
                }
                """;

        Files.createDirectories(settingsPath.getParent());
        Files.writeString(settingsPath, legacyJson);

        settingsManager.load();
        UserPreferences migratedPrefs = settingsManager.getPreferences();

        assertThat(migratedPrefs).isNotNull();
        List<UserPreferences.ColumnConfig> cols = migratedPrefs.getColumns();

        // 1. Must be migrated to 15 columns
        assertThat(cols).hasSize(15);

        // 2. Prepended Status column at index 0
        assertThat(cols.get(0).getModelIndex()).isEqualTo(0);
        assertThat(cols.get(0).getWidth()).isEqualTo(30);
        assertThat(cols.get(0).isVisible()).isTrue();

        // 3. Former legacy 0 (Track#) shifted to model index 1
        assertThat(cols.get(1).getModelIndex()).isEqualTo(1);
        assertThat(cols.get(1).getWidth()).isEqualTo(50);
        assertThat(cols.get(1).isVisible()).isTrue();

        // 4. Former legacy 1 (Title) shifted to model index 2
        assertThat(cols.get(2).getModelIndex()).isEqualTo(2);
        assertThat(cols.get(2).getWidth()).isEqualTo(250);
        assertThat(cols.get(2).isVisible()).isTrue();

        // 5. Former legacy 13 (Rating) shifted to model index 14
        assertThat(cols.get(14).getModelIndex()).isEqualTo(14);
        assertThat(cols.get(14).getWidth()).isEqualTo(85);
        assertThat(cols.get(14).isVisible()).isTrue();

        // 6. Sort index migrated from legacy Title (modelIndex 1) to shifted Title (modelIndex 2)
        assertThat(migratedPrefs.getSort().getModelIndex()).isEqualTo(2);
        assertThat(migratedPrefs.getSort().getSortOrder()).isEqualTo("ASCENDING");
    }
}

