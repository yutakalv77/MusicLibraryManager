package com.musiclibrary.i18n;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages ResourceBundle loading, message formatting, and dynamic language switching.
 * Thread-safe with atomic JSON configuration persistence.
 */
public class I18nManager {

    private static volatile I18nManager instance;

    private static final String BUNDLE_BASE_NAME = "messages";

    private static final Path DEFAULT_CONFIG_PATH = Paths.get(
            System.getProperty("user.home"),
            ".musiclibrary",
            "i18n-config.json"
    );

    private final Path configPath;
    private final Gson gson;
    private final List<LanguageChangeListener> listeners;

    private volatile AppLocale currentLocale;
    private volatile ResourceBundle bundle;

    /**
     * Obtains the global singleton instance of I18nManager.
     *
     * @return global I18nManager instance
     */
    public static I18nManager getInstance() {
        if (instance == null) {
            synchronized (I18nManager.class) {
                if (instance == null) {
                    instance = new I18nManager();
                }
            }
        }
        return instance;
    }

    /**
     * Default constructor using default config path.
     */
    public I18nManager() {
        this(DEFAULT_CONFIG_PATH);
    }

    /**
     * Constructor allowing custom config file location (ideal for testing).
     *
     * @param configPath path to language config JSON file
     */
    public I18nManager(Path configPath) {
        this.configPath = configPath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.listeners = new CopyOnWriteArrayList<>();
        this.currentLocale = AppLocale.SYSTEM;
        loadBundle();
    }

    /**
     * Initializes the manager, restoring saved locale from configuration if available.
     */
    public void init() {
        AppLocale saved = loadLocaleConfig();
        setLocale(saved != null ? saved : AppLocale.SYSTEM);
    }

    /**
     * Initializes the manager with an explicit initial locale.
     *
     * @param initialLocale initial locale to apply
     */
    public void init(AppLocale initialLocale) {
        setLocale(initialLocale != null ? initialLocale : AppLocale.SYSTEM);
    }

    /**
     * Sets a new application locale, reloads the resource bundle, and notifies listeners.
     *
     * @param appLocale locale to apply
     */
    public void setLocale(AppLocale appLocale) {
        this.currentLocale = (appLocale != null) ? appLocale : AppLocale.SYSTEM;
        loadBundle();
        notifyListeners(this.currentLocale);
        saveLocaleConfig();
    }

    private void loadBundle() {
        Locale effectiveLocale = currentLocale.getLocale();
        try {
            this.bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, effectiveLocale);
        } catch (MissingResourceException e) {
            // Fallback to root bundle
            this.bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ROOT);
        }
    }

    /**
     * Retrieves the current AppLocale.
     *
     * @return current AppLocale
     */
    public AppLocale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Retrieves the effective java.util.Locale.
     *
     * @return effective Locale
     */
    public Locale getLocale() {
        return currentLocale.getLocale();
    }

    /**
     * Retrieves the localized message for the specified key.
     * If the key is not found, returns "!key!" as a safe fallback.
     *
     * @param key message key
     * @return localized message or fallback string
     */
    public String get(String key) {
        if (key == null) {
            return "";
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    /**
     * Retrieves the localized message and formats it with the specified arguments.
     *
     * @param key  message key
     * @param args arguments for formatting
     * @return formatted localized message
     */
    public String format(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    // --- Listener Management ---

    /**
     * Adds a language change listener.
     *
     * @param listener listener to register
     */
    public void addLanguageChangeListener(LanguageChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a language change listener.
     *
     * @param listener listener to unregister
     */
    public void removeLanguageChangeListener(LanguageChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners(AppLocale newLocale) {
        for (LanguageChangeListener listener : listeners) {
            try {
                listener.onLanguageChanged(newLocale);
            } catch (Exception e) {
                // Prevent one failing listener from disrupting others
            }
        }
    }

    // --- Persistence ---

    /**
     * Loads saved locale from config file.
     *
     * @return loaded AppLocale, or null if not found/corrupted
     */
    public AppLocale loadLocaleConfig() {
        if (configPath == null || !Files.exists(configPath) || !Files.isRegularFile(configPath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            LocaleConfig config = gson.fromJson(reader, LocaleConfig.class);
            if (config != null && config.locale != null) {
                return AppLocale.fromCode(config.locale);
            }
        } catch (Exception e) {
            // Ignore corruption and fallback
        }
        return null;
    }

    /**
     * Saves current locale configuration atomically.
     */
    public void saveLocaleConfig() {
        if (configPath == null) {
            return;
        }

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            LocaleConfig config = new LocaleConfig();
            config.locale = (currentLocale != null ? currentLocale : AppLocale.SYSTEM).name();

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
            // Ignore persistence error
        }
    }

    /**
     * DTO for persisting locale preference.
     */
    static class LocaleConfig {
        String locale;
    }
}
