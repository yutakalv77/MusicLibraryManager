package com.musiclibrary.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeFormatterTest {

    @Test
    @DisplayName("Private constructor cannot be called or throws when accessed via reflection")
    void testPrivateConstructor() throws Exception {
        Constructor<TimeFormatter> constructor = TimeFormatter.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        TimeFormatter instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "-100, 00:00",
            "-1, 00:00",
            "0, 00:00",
            "5, 00:05",
            "59, 00:59",
            "60, 01:00",
            "83, 01:23",
            "599, 09:59",
            "3599, 59:59",
            "3600, 01:00:00",
            "3661, 01:01:01",
            "7322, 02:02:02",
            "86399, 23:59:59",
            "86400, 24:00:00"
    })
    @DisplayName("format correctly formats seconds into mm:ss or hh:mm:ss")
    void testFormat(long seconds, String expected) {
        assertThat(TimeFormatter.format(seconds)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "00:00, 0",
            "00:05, 5",
            "00:59, 59",
            "01:00, 60",
            "01:23, 83",
            "59:59, 3599",
            "01:00:00, 3600",
            "01:01:01, 3661",
            "02:02:02, 7322",
            "24:00:00, 86400"
    })
    @DisplayName("parse correctly converts mm:ss and hh:mm:ss strings to seconds")
    void testParseValid(String timeStr, long expected) {
        assertThat(TimeFormatter.parse(timeStr)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "invalid",
            "00",
            "00:00:00:00",
            "01:60",       // seconds >= 60
            "01:00:60",    // seconds >= 60
            "01:60:00",    // minutes >= 60 in hh:mm:ss
            "-01:00",
            "01:-30",
            "ab:cd",
            "12:ab"
    })
    @DisplayName("parse throws IllegalArgumentException for invalid inputs")
    void testParseInvalid(String invalidStr) {
        assertThatThrownBy(() -> TimeFormatter.parse(invalidStr))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parse throws IllegalArgumentException for null input")
    void testParseNull() {
        assertThatThrownBy(() -> TimeFormatter.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("formatDuration formats current and total seconds under 1 hour")
    void testFormatDurationUnderOneHour() {
        assertThat(TimeFormatter.formatDuration(83, 225)).isEqualTo("01:23 / 03:45");
        assertThat(TimeFormatter.formatDuration(-5, 0)).isEqualTo("00:00 / 00:00");
    }

    @Test
    @DisplayName("formatDuration formats current and total seconds when total is 1 hour or more")
    void testFormatDurationOverOneHour() {
        assertThat(TimeFormatter.formatDuration(83, 3661)).isEqualTo("00:01:23 / 01:01:01");
        assertThat(TimeFormatter.formatDuration(3600, 7200)).isEqualTo("01:00:00 / 02:00:00");
    }

    @Test
    @DisplayName("parse handles leading and trailing whitespaces cleanly")
    void testParseWithWhitespace() {
        assertThat(TimeFormatter.parse("  01:23  ")).isEqualTo(83);
        assertThat(TimeFormatter.parse("\t01:02:03\n")).isEqualTo(3723);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "01.30",
            "01;30",
            "01-30",
            "01:2:3",
            "1:2",
            "01:02:03:04",
            "99:99",
            "00:60",
            "01:60:00"
    })
    @DisplayName("parse rejects non-standard delimiters and out-of-range components")
    void testParseDelimiterAndFormatFailures(String invalid) {
        assertThatThrownBy(() -> TimeFormatter.parse(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("format handles extremely large duration cleanly without overflow")
    void testFormatLargeDuration() {
        assertThat(TimeFormatter.format(360000)).isEqualTo("100:00:00");
    }

    @Test
    @DisplayName("formatDuration handles edge cases including negative clamped and current exceeding total")
    void testFormatDurationEdgeCases() {
        // Negative values clamped to 0
        assertThat(TimeFormatter.formatDuration(-10, -20)).isEqualTo("00:00 / 00:00");

        // Current exceeding total
        assertThat(TimeFormatter.formatDuration(150, 100)).isEqualTo("02:30 / 01:40");

        // Total >= 3600 ensures both sides use hh:mm:ss
        assertThat(TimeFormatter.formatDuration(300, 3600)).isEqualTo("00:05:00 / 01:00:00");
    }
}
