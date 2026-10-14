package com.musiclibrary.i18n;

/**
 * Functional listener interface for observing application language change events.
 */
@FunctionalInterface
public interface LanguageChangeListener {

    /**
     * Invoked when the application locale has changed.
     *
     * @param newLocale the newly applied locale
     */
    void onLanguageChanged(AppLocale newLocale);
}
