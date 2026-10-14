package com.musiclibrary.theme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppThemeTest {

    @Test
    @DisplayName("AppTheme contains exactly DARK, LIGHT, and SYSTEM")
    void testEnumValues() {
        assertThat(AppTheme.values()).containsExactly(
                AppTheme.DARK,
                AppTheme.LIGHT,
                AppTheme.SYSTEM
        );
        assertThat(AppTheme.values()).hasSize(3);
    }

    @Test
    @DisplayName("valueOf works correctly for exact valid theme names")
    void testValueOf() {
        assertThat(AppTheme.valueOf("DARK")).isEqualTo(AppTheme.DARK);
        assertThat(AppTheme.valueOf("LIGHT")).isEqualTo(AppTheme.LIGHT);
        assertThat(AppTheme.valueOf("SYSTEM")).isEqualTo(AppTheme.SYSTEM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dark", "light", "System", "CUSTOM", "", "   "})
    @DisplayName("valueOf throws IllegalArgumentException for invalid theme names")
    void testValueOfInvalid(String invalidName) {
        assertThatThrownBy(() -> AppTheme.valueOf(invalidName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("valueOf throws NullPointerException when null is passed")
    void testValueOfNull() {
        assertThatThrownBy(() -> AppTheme.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("AppTheme supports comparison, ordering, and equality contracts")
    void testComparisonAndOrdering() {
        assertThat(AppTheme.DARK).isEqualByComparingTo(AppTheme.DARK);
        assertThat(AppTheme.DARK).isNotEqualTo(AppTheme.LIGHT);
        assertThat(AppTheme.DARK.compareTo(AppTheme.LIGHT)).isLessThan(0);
        assertThat(AppTheme.LIGHT.compareTo(AppTheme.SYSTEM)).isLessThan(0);

        assertThat(AppTheme.DARK.ordinal()).isEqualTo(0);
        assertThat(AppTheme.LIGHT.ordinal()).isEqualTo(1);
        assertThat(AppTheme.SYSTEM.ordinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("AppTheme works comprehensively in switch statements")
    void testSwitchCoverage() {
        for (AppTheme theme : AppTheme.values()) {
            String label = switch (theme) {
                case DARK -> "dark_mode";
                case LIGHT -> "light_mode";
                case SYSTEM -> "system_mode";
            };
            assertThat(label).isNotBlank();
        }
    }
}
