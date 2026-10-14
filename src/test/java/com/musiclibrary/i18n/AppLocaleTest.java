package com.musiclibrary.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AppLocaleTest {

    @Test
    @DisplayName("AppLocale contains exactly ENGLISH, JAPANESE, and SYSTEM")
    void testEnumValues() {
        assertThat(AppLocale.values()).containsExactly(
                AppLocale.ENGLISH,
                AppLocale.JAPANESE,
                AppLocale.SYSTEM
        );
        assertThat(AppLocale.values()).hasSize(3);
    }

    @Test
    @DisplayName("Locales return correct codes, display names, and locale objects")
    void testProperties() {
        assertThat(AppLocale.ENGLISH.getCode()).isEqualTo("en");
        assertThat(AppLocale.ENGLISH.getDisplayName()).isEqualTo("English");
        assertThat(AppLocale.ENGLISH.getLocale()).isEqualTo(Locale.ENGLISH);

        assertThat(AppLocale.JAPANESE.getCode()).isEqualTo("ja");
        assertThat(AppLocale.JAPANESE.getDisplayName()).isEqualTo("日本語");
        assertThat(AppLocale.JAPANESE.getLocale()).isEqualTo(Locale.JAPANESE);

        assertThat(AppLocale.SYSTEM.getCode()).isEqualTo("");
        assertThat(AppLocale.SYSTEM.getDisplayName()).isEqualTo("System Default");
        assertThat(AppLocale.SYSTEM.getLocale()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "en, ENGLISH",
            "EN, ENGLISH",
            "'  en  ', ENGLISH",
            "english, ENGLISH",
            "English, ENGLISH",
            "'  ENGLISH  ', ENGLISH",
            "ja, JAPANESE",
            "JA, JAPANESE",
            "'  ja  ', JAPANESE",
            "japanese, JAPANESE",
            "Japanese, JAPANESE",
            "'  JAPANESE  ', JAPANESE",
            "'', SYSTEM",
            "'   ', SYSTEM",
            "system, SYSTEM",
            "SYSTEM, SYSTEM",
            "'  system  ', SYSTEM",
            "unknown, SYSTEM",
            "fr, SYSTEM",
            "de, SYSTEM",
            "12345, SYSTEM"
    })
    @DisplayName("fromCode parses various valid codes, enum names, whitespaces, and safely falls back to SYSTEM")
    void testFromCode(String input, AppLocale expected) {
        assertThat(AppLocale.fromCode(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("fromCode with null safely returns SYSTEM")
    void testFromCodeNull() {
        assertThat(AppLocale.fromCode(null)).isEqualTo(AppLocale.SYSTEM);
    }

    @Test
    @DisplayName("SYSTEM locale resolves to Japanese if host OS default is Japanese, otherwise English")
    void testSystemLocaleResolution() {
        Locale defaultLocale = Locale.getDefault();
        Locale resolved = AppLocale.SYSTEM.getLocale();

        assertThat(resolved).isNotNull();
        if (Locale.JAPANESE.getLanguage().equalsIgnoreCase(defaultLocale.getLanguage())) {
            assertThat(resolved).isEqualTo(Locale.JAPANESE);
        } else {
            assertThat(resolved).isEqualTo(Locale.ENGLISH);
        }
    }

    @Test
    @DisplayName("AppLocale supports comparison, ordering, and equality contracts")
    void testComparisonAndOrdering() {
        assertThat(AppLocale.ENGLISH).isEqualByComparingTo(AppLocale.ENGLISH);
        assertThat(AppLocale.ENGLISH).isNotEqualTo(AppLocale.JAPANESE);
        assertThat(AppLocale.ENGLISH.compareTo(AppLocale.JAPANESE)).isLessThan(0);
        assertThat(AppLocale.JAPANESE.compareTo(AppLocale.SYSTEM)).isLessThan(0);

        assertThat(AppLocale.ENGLISH.ordinal()).isEqualTo(0);
        assertThat(AppLocale.JAPANESE.ordinal()).isEqualTo(1);
        assertThat(AppLocale.SYSTEM.ordinal()).isEqualTo(2);
    }
}
