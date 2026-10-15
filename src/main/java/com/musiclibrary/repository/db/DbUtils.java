package com.musiclibrary.repository.db;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Utility functions for database record mapping and data parsing.
 */
public final class DbUtils {

    private DbUtils() {
        // Utility class
    }

    /**
     * Parses an ISO-8601 date-time string into an {@link Instant}.
     * Returns {@link Instant#now()} if text is null, blank, or unparseable.
     *
     * @param text date-time string in ISO-8601 format
     * @return parsed Instant or Instant.now()
     */
    public static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Instant.now();
        }
    }

    /**
     * Parses an ISO-8601 date-time string into an {@link Instant}.
     * Returns {@code null} if text is null, blank, or unparseable.
     *
     * @param text date-time string in ISO-8601 format
     * @return parsed Instant or null
     */
    public static Instant parseNullableInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }
}
