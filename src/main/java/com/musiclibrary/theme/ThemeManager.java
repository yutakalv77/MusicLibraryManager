package com.musiclibrary.theme;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages application Look & Feel configuration and dynamic switching using FlatLaf.
 * Supports DARK (macOS Dark), LIGHT (macOS Light), and SYSTEM (OS-detected) modes,
 * theme persistence, change notification listeners, and smooth animated transitions.
 */
public class ThemeManager {

    private static volatile ThemeManager instance;

    private static final Path DEFAULT_CONFIG_PATH = Paths.get(
            System.getProperty("user.home"),
            ".musiclibrary",
            "theme-config.json"
    );

    private final Path configPath;
    private final Gson gson;
    private final List<ThemeChangeListener> listeners;
    private volatile AppTheme currentTheme;

    /**
     * Obtains the global singleton instance of ThemeManager.
     *
     * @return global ThemeManager instance
     */
    public static ThemeManager getInstance() {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager();
                }
            }
        }
        return instance;
    }

    /**
     * Default constructor using default user home config path.
     */
    public ThemeManager() {
        this(DEFAULT_CONFIG_PATH);
    }

    /**
     * Constructor allowing custom config file location (ideal for testing).
     *
     * @param configPath path to theme config JSON file
     */
    public ThemeManager(Path configPath) {
        this.configPath = configPath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.listeners = new CopyOnWriteArrayList<>();
        this.currentTheme = AppTheme.DARK;
    }

    /**
     * Initializes the theme manager.
     * Restores saved theme from configuration if available; otherwise applies default theme.
     */
    public void init() {
        AppTheme savedTheme = loadThemeConfig();
        applyTheme(savedTheme != null ? savedTheme : AppTheme.DARK);
    }

    /**
     * Initializes the theme manager with an explicit initial theme.
     *
     * @param initialTheme initial theme to apply
     */
    public void init(AppTheme initialTheme) {
        applyTheme(initialTheme != null ? initialTheme : AppTheme.DARK);
    }

    /**
     * Applies the specified theme dynamically without application restart.
     *
     * @param theme theme to apply
     */
    public void applyTheme(AppTheme theme) {
        final AppTheme targetTheme = (theme != null) ? theme : AppTheme.DARK;
        this.currentTheme = targetTheme;
        final boolean darkMode = isDarkMode();

        Runnable task = () -> {
            boolean headless = GraphicsEnvironment.isHeadless();

            if (!headless) {
                FlatAnimatedLafChange.showSnapshot();
            }

            if (darkMode) {
                FlatMacDarkLaf.setup();
            } else {
                FlatMacLightLaf.setup();
            }

            if (!headless) {
                FlatLaf.updateUI();
                FlatAnimatedLafChange.hideSnapshotWithAnimation();
            }

            notifyListeners(targetTheme);
        };

        if (SwingUtilities.isEventDispatchThread() || GraphicsEnvironment.isHeadless()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (Exception e) {
                task.run();
            }
        }

        saveThemeConfig();
    }

    /**
     * Toggles between DARK and LIGHT themes.
     * If current theme is SYSTEM, it toggles to the opposite of current effective mode.
     */
    public void toggleTheme() {
        if (currentTheme == AppTheme.DARK) {
            applyTheme(AppTheme.LIGHT);
        } else if (currentTheme == AppTheme.LIGHT) {
            applyTheme(AppTheme.DARK);
        } else {
            // If SYSTEM, flip based on effective dark mode
            if (isDarkMode()) {
                applyTheme(AppTheme.LIGHT);
            } else {
                applyTheme(AppTheme.DARK);
            }
        }
    }

    /**
     * Gets the currently configured AppTheme.
     *
     * @return current configured AppTheme
     */
    public AppTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Checks if the currently active UI is in dark mode.
     *
     * @return true if dark mode is active, false otherwise
     */
    public boolean isDarkMode() {
        return switch (currentTheme) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> isSystemInDarkMode();
        };
    }

    /**
     * Adds a theme change listener.
     *
     * @param listener listener to register
     */
    public void addThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a theme change listener.
     *
     * @param listener listener to unregister
     */
    public void removeThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(AppTheme newTheme) {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(newTheme);
            } catch (Exception e) {
                // Prevent one failing listener from blocking others
            }
        }
    }

    // --- Persistence ---

    /**
     * Loads theme configuration from JSON file.
     *
     * @return loaded AppTheme, or null if file not found or corrupted
     */
    public AppTheme loadThemeConfig() {
        if (configPath == null || !Files.exists(configPath) || !Files.isRegularFile(configPath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ThemeConfig config = gson.fromJson(reader, ThemeConfig.class);
            if (config != null && config.theme != null) {
                return AppTheme.valueOf(config.theme.trim().toUpperCase(Locale.ROOT));
            }
        } catch (Exception e) {
            // Ignore and fallback
        }
        return null;
    }

    /**
     * Saves current theme configuration to JSON file using atomic write.
     */
    public void saveThemeConfig() {
        if (configPath == null) {
            return;
        }

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            ThemeConfig config = new ThemeConfig();
            config.theme = (currentTheme != null ? currentTheme : AppTheme.DARK).name();

            String fileName = configPath.getFileName().toString();
            Path tempPath = configPath.resolveSibling(fileName + ".tmp");

            try {
                try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                    gson.toJson(config, writer);
                }

                try {
                    Files.move(tempPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                }
            }
        } catch (Exception e) {
            // Persistence failure should not crash theme switching
        }
    }

    /**
     * Detects host OS dark mode preference.
     *
     * @return true if system is in dark mode, false otherwise
     */
    boolean isSystemInDarkMode() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        try {
            if (osName.contains("win")) {
                return isWindowsInDarkMode();
            } else if (osName.contains("mac")) {
                return isMacInDarkMode();
            } else if (osName.contains("linux")) {
                return isLinuxInDarkMode();
            }
        } catch (Exception ignored) {
        }

        // Fallback default is dark mode
        return true;
    }

    private boolean isWindowsInDarkMode() {
        try {
            Process process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
            ).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("AppsUseLightTheme")) {
                        // 0x0 indicates Dark mode, 0x1 indicates Light mode
                        if (line.contains("0x0")) {
                            return true;
                        } else if (line.contains("0x1")) {
                            return false;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean isMacInDarkMode() {
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line != null && line.trim().equalsIgnoreCase("Dark");
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isLinuxInDarkMode() {
        try {
            Process process = new ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line != null && line.contains("prefer-dark");
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    /**
     * Simple DTO for theme persistence.
     */
    static class ThemeConfig {
        String theme;
    }
}
