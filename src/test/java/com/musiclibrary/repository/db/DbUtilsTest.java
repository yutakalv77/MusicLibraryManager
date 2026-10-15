package com.musiclibrary.repository.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DbUtilsTest {

    @Test
    @DisplayName("parseInstant correctly parses valid ISO-8601 strings")
    void testParseInstantValid() {
        String iso = "2026-10-15T12:34:56.789Z";
        Instant parsed = DbUtils.parseInstant(iso);

        assertThat(parsed).isEqualTo(Instant.parse(iso));
    }

    @Test
    @DisplayName("parseInstant falls back to Instant.now() for null, blank, or invalid strings")
    void testParseInstantFallback() {
        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

        Instant parsedNull = DbUtils.parseInstant(null);
        assertThat(parsedNull).isAfterOrEqualTo(before);

        Instant parsedEmpty = DbUtils.parseInstant("");
        assertThat(parsedEmpty).isAfterOrEqualTo(before);

        Instant parsedBlank = DbUtils.parseInstant("   ");
        assertThat(parsedBlank).isAfterOrEqualTo(before);

        Instant parsedInvalid = DbUtils.parseInstant("not-a-date");
        assertThat(parsedInvalid).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("parseNullableInstant correctly parses valid ISO-8601 strings")
    void testParseNullableInstantValid() {
        String iso = "2026-10-15T12:34:56.789Z";
        Instant parsed = DbUtils.parseNullableInstant(iso);

        assertThat(parsed).isEqualTo(Instant.parse(iso));
    }

    @Test
    @DisplayName("parseNullableInstant returns null for null, blank, or invalid strings")
    void testParseNullableInstantFallback() {
        assertThat(DbUtils.parseNullableInstant(null)).isNull();
        assertThat(DbUtils.parseNullableInstant("")).isNull();
        assertThat(DbUtils.parseNullableInstant("   ")).isNull();
        assertThat(DbUtils.parseNullableInstant("invalid-iso-timestamp")).isNull();
    }

    @Test
    @DisplayName("DbUtils private constructor can be invoked via reflection for coverage")
    void testPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<DbUtils> constructor = DbUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        DbUtils instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}

