package com.musiclibrary.theme;

/**
 * Functional listener interface for observing application theme change events.
 */
@FunctionalInterface
public interface ThemeChangeListener {

    /**
     * Invoked when the application theme has changed.
     *
     * @param newTheme the newly applied theme
     */
    void onThemeChanged(AppTheme newTheme);
}
