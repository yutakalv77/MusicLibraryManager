package com.musiclibrary.i18n;

import java.util.Locale;

/**
 * Enumeration of supported application locales.
 */
public enum AppLocale {

    /**
     * English locale ("en").
     */
    ENGLISH("en", "English", Locale.ENGLISH),

    /**
     * Japanese locale ("ja").
     */
    JAPANESE("ja", "日本語", Locale.JAPANESE),

    /**
     * Follows the host system's default locale.
     */
    SYSTEM("", "System Default", null);

    private final String code;
    private final String displayName;
    private final Locale locale;

    AppLocale(String code, String displayName, Locale locale) {
        this.code = code;
        this.displayName = displayName;
        this.locale = locale;
    }

    /**
     * Returns the language code string (e.g., "en", "ja", or "" for system).
     *
     * @return language code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the human-readable display name.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the effective java.util.Locale instance.
     * For SYSTEM, resolves to Locale.getDefault().
     *
     * @return effective Locale
     */
    public Locale getLocale() {
        if (this == SYSTEM) {
            Locale defaultLocale = Locale.getDefault();
            // If system language is Japanese, map to JAPANESE; otherwise fallback to ENGLISH
            if (Locale.JAPANESE.getLanguage().equalsIgnoreCase(defaultLocale.getLanguage())) {
                return Locale.JAPANESE;
            }
            return Locale.ENGLISH;
        }
        return locale;
    }

    /**
     * Safely parses language code or enum name into AppLocale.
     *
     * @param value code (e.g. "en", "ja") or enum name ("ENGLISH", "JAPANESE", "SYSTEM")
     * @return matched AppLocale, or SYSTEM if null/unrecognized
     */
    public static AppLocale fromCode(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AppLocale appLocale : values()) {
            if (appLocale.name().toLowerCase(Locale.ROOT).equals(normalized)
                    || appLocale.code.toLowerCase(Locale.ROOT).equals(normalized)) {
                return appLocale;
            }
        }

        return SYSTEM;
    }
}
