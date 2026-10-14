package com.musiclibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybackStateTest {

    @Test
    @DisplayName("PlaybackState should define exactly STOPPED, PLAYING, and PAUSED")
    void testEnumValues() {
        assertThat(PlaybackState.values())
                .containsExactly(PlaybackState.STOPPED, PlaybackState.PLAYING, PlaybackState.PAUSED);
        assertThat(PlaybackState.values()).hasSize(3);
    }

    @Test
    @DisplayName("PlaybackState valueOf works correctly for valid enum names")
    void testValueOf() {
        assertThat(PlaybackState.valueOf("STOPPED")).isEqualTo(PlaybackState.STOPPED);
        assertThat(PlaybackState.valueOf("PLAYING")).isEqualTo(PlaybackState.PLAYING);
        assertThat(PlaybackState.valueOf("PAUSED")).isEqualTo(PlaybackState.PAUSED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stopped", "Playing", "IDLE", "UNKNOWN", "", "   "})
    @DisplayName("PlaybackState valueOf throws IllegalArgumentException for invalid names")
    void testValueOfInvalid(String invalidName) {
        assertThatThrownBy(() -> PlaybackState.valueOf(invalidName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PlaybackState valueOf throws NullPointerException when null is passed")
    void testValueOfNull() {
        assertThatThrownBy(() -> PlaybackState.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("PlaybackState constants support equality, comparison, and ordinal ordering")
    void testEnumComparisonAndOrdering() {
        assertThat(PlaybackState.STOPPED).isEqualByComparingTo(PlaybackState.STOPPED);
        assertThat(PlaybackState.STOPPED).isNotEqualTo(PlaybackState.PLAYING);
        assertThat(PlaybackState.STOPPED.compareTo(PlaybackState.PLAYING)).isLessThan(0);
        assertThat(PlaybackState.PLAYING.compareTo(PlaybackState.PAUSED)).isLessThan(0);

        assertThat(PlaybackState.STOPPED.ordinal()).isEqualTo(0);
        assertThat(PlaybackState.PLAYING.ordinal()).isEqualTo(1);
        assertThat(PlaybackState.PAUSED.ordinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("PlaybackState works in pattern matching / switch statements comprehensively")
    void testSwitchStatementCoverage() {
        for (PlaybackState state : PlaybackState.values()) {
            String label = switch (state) {
                case STOPPED -> "stopped_state";
                case PLAYING -> "playing_state";
                case PAUSED -> "paused_state";
            };
            assertThat(label).isNotBlank();
        }
    }
}
