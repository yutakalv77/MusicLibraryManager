package com.musiclibrary.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.MainFrame;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Singleton manager coordinating persistence and lifecycle application of
 * UserPreferences to and from the GUI and runtime subsystems.
 */
public class SettingsManager {

    private static final System.Logger LOGGER = System.getLogger(SettingsManager.class.getName());
    public static final String SETTINGS_PATH_PROPERTY = "musiclibrary.settings.path";

    private static final SettingsManager INSTANCE = new SettingsManager();

    private final Path settingsPath;
    private final Gson gson;
    private UserPreferences preferences;
    private final Object lock = new Object();

    public static SettingsManager getInstance() {
        return INSTANCE;
    }

    public SettingsManager() {
        this(resolveDefaultSettingsPath());
    }

    public SettingsManager(Path settingsPath) {
        this.settingsPath = settingsPath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.preferences = new UserPreferences();
    }

    public static Path resolveDefaultSettingsPath() {
        String prop = System.getProperty(SETTINGS_PATH_PROPERTY);
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop);
        }
        return Paths.get(System.getProperty("user.home"), ".musiclibrary", "settings.json");
    }

    public Path getSettingsPath() {
        return settingsPath;
    }

    public UserPreferences getPreferences() {
        synchronized (lock) {
            return preferences;
        }
    }

    public void setPreferences(UserPreferences preferences) {
        synchronized (lock) {
            this.preferences = preferences != null ? preferences : new UserPreferences();
        }
    }

    /**
     * Loads user preferences from the settings JSON file on disk.
     * Falls back to default preferences if the file is absent or invalid.
     */
    public void load() {
        synchronized (lock) {
            if (settingsPath == null || !Files.exists(settingsPath)) {
                preferences = new UserPreferences();
                return;
            }

            try (BufferedReader reader = Files.newBufferedReader(settingsPath)) {
                UserPreferences loaded = gson.fromJson(reader, UserPreferences.class);
                if (loaded != null) {
                    loaded.migrateLegacyColumns();
                    preferences = loaded;
                } else {
                    preferences = new UserPreferences();
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to load settings from " + settingsPath + ": " + e.getMessage(), e);
                preferences = new UserPreferences();
            }
        }
    }

    /**
     * Atomically saves the current preferences to the settings JSON file on disk.
     */
    public void save() {
        synchronized (lock) {
            if (settingsPath == null) {
                return;
            }

            try {
                Path parent = settingsPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                Path tempFile = Files.createTempFile(parent != null ? parent : Paths.get("."), "settings_", ".tmp");
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
                    gson.toJson(preferences, writer);
                }

                try {
                    Files.move(tempFile, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    Files.move(tempFile, settingsPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.ERROR, "Failed to save settings to " + settingsPath + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Applies theme and language settings early prior to UI window creation.
     */
    public void applyThemeAndLanguageEarly() {
        UserPreferences prefs = getPreferences();

        // 1. Theme
        try {
            AppTheme theme = AppTheme.valueOf(prefs.getTheme());
            ThemeManager.getInstance().applyTheme(theme);
        } catch (Exception e) {
            ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        }

        // 2. Language
        try {
            AppLocale locale = AppLocale.valueOf(prefs.getLanguage());
            I18nManager.getInstance().setLocale(locale);
        } catch (Exception e) {
            I18nManager.getInstance().setLocale(AppLocale.SYSTEM);
        }
    }

    /**
     * Restores window position, dimensions, maximized state, volume, and track table layout to the given MainFrame.
     *
     * @param frame active MainFrame instance
     */
    public void applyTo(MainFrame frame) {
        if (frame == null) {
            return;
        }

        UserPreferences prefs = getPreferences();

        // 1. Window bounds & state
        int x = prefs.getWindowX();
        int y = prefs.getWindowY();
        int w = prefs.getWindowWidth();
        int h = prefs.getWindowHeight();
        boolean maximized = prefs.isWindowMaximized();

        if (x >= 0 && y >= 0 && !GraphicsEnvironment.isHeadless()) {
            Rectangle windowRect = new Rectangle(x, y, w, h);
            boolean onScreen = false;
            try {
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                for (GraphicsDevice gd : ge.getScreenDevices()) {
                    Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();
                    if (screenBounds.intersects(windowRect)) {
                        onScreen = true;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }

            if (onScreen) {
                frame.setBounds(x, y, w, h);
            } else {
                frame.setSize(w, h);
                frame.setLocationRelativeTo(null);
            }
        } else {
            frame.setSize(w, h);
            frame.setLocationRelativeTo(null);
        }

        if (maximized) {
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }

        // 2. Playback Volume
        if (frame.getPlayerController() != null) {
            frame.getPlayerController().setVolumePercent(prefs.getVolumePercent());
        }

        // 3. Track Table Columns & Sorting
        if (frame.getTrackTablePanel() != null) {
            frame.getTrackTablePanel().applyColumnConfigs(prefs.getColumns());
            frame.getTrackTablePanel().applySortConfig(prefs.getSort());
        }
    }

    /**
     * Captures the current state of MainFrame and runtime subsystems, updates preferences,
     * and persists them to disk.
     *
     * @param frame active MainFrame instance
     */
    public void saveFrom(MainFrame frame) {
        if (frame == null) {
            return;
        }

        synchronized (lock) {
            // 1. Window bounds & maximized state
            int state = frame.getExtendedState();
            boolean maximized = (state & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            preferences.setWindowMaximized(maximized);

            if (!maximized) {
                Point pos = frame.getLocation();
                Dimension size = frame.getSize();
                preferences.setWindowX(pos.x);
                preferences.setWindowY(pos.y);
                preferences.setWindowWidth(size.width);
                preferences.setWindowHeight(size.height);
            }

            // 2. Theme & Language
            preferences.setTheme(ThemeManager.getInstance().getCurrentTheme().name());
            preferences.setLanguage(I18nManager.getInstance().getCurrentLocale().name());

            // 3. Playback Volume
            if (frame.getPlayerController() != null) {
                preferences.setVolumePercent(frame.getPlayerController().getVolumePercent());
            }

            // 4. Track Table Columns & Sorting
            if (frame.getTrackTablePanel() != null) {
                preferences.setColumns(frame.getTrackTablePanel().exportColumnConfigs());
                preferences.setSort(frame.getTrackTablePanel().exportSortConfig());
            }

            save();
        }
    }
}
