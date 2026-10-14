package com.musiclibrary.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for formatting and parsing audio duration and playback timestamps.
 */
public final class TimeFormatter {

    private static final Pattern MM_SS_PATTERN = Pattern.compile("^(\\d+):([0-5]\\d)$");
    private static final Pattern HH_MM_SS_PATTERN = Pattern.compile("^(\\d+):([0-5]\\d):([0-5]\\d)$");

    private TimeFormatter() {
        // Prevent instantiation
    }

    /**
     * Formats seconds into "mm:ss" if under 1 hour, or "hh:mm:ss" if 1 hour or more.
     * Negative values are clamped to "00:00".
     *
     * @param seconds duration in seconds
     * @return formatted time string
     */
    public static String format(long seconds) {
        if (seconds <= 0) {
            return "00:00";
        }

        if (seconds < 3600) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return String.format("%02d:%02d", mins, secs);
        } else {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return String.format("%02d:%02d:%02d", hours, mins, secs);
        }
    }

    /**
     * Formats current and total playback position as "mm:ss / mm:ss" (or "hh:mm:ss / hh:mm:ss").
     *
     * @param currentSeconds elapsed seconds
     * @param totalSeconds   total duration seconds
     * @return formatted duration string (e.g. "01:23 / 03:45")
     */
    public static String formatDuration(long currentSeconds, long totalSeconds) {
        long safeCurrent = Math.max(0, currentSeconds);
        long safeTotal = Math.max(0, totalSeconds);

        if (safeTotal >= 3600) {
            return formatHours(safeCurrent) + " / " + formatHours(safeTotal);
        } else {
            return format(safeCurrent) + " / " + format(safeTotal);
        }
    }

    private static String formatHours(long seconds) {
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, mins, secs);
    }

    /**
     * Parses a time string in "mm:ss" or "hh:mm:ss" format into total seconds.
     *
     * @param timeStr time string
     * @return total seconds
     * @throws IllegalArgumentException if format is invalid, null, blank, or seconds/minutes out of bounds
     */
    public static long parse(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            throw new IllegalArgumentException("Time string cannot be null or blank");
        }

        String trimmed = timeStr.trim();

        Matcher mmssMatcher = MM_SS_PATTERN.matcher(trimmed);
        if (mmssMatcher.matches()) {
            try {
                long mins = Long.parseLong(mmssMatcher.group(1));
                long secs = Long.parseLong(mmssMatcher.group(2));
                return mins * 60 + secs;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number in time string: " + timeStr, e);
            }
        }

        Matcher hhmmssMatcher = HH_MM_SS_PATTERN.matcher(trimmed);
        if (hhmmssMatcher.matches()) {
            try {
                long hours = Long.parseLong(hhmmssMatcher.group(1));
                long mins = Long.parseLong(hhmmssMatcher.group(2));
                long secs = Long.parseLong(hhmmssMatcher.group(3));
                return hours * 3600 + mins * 60 + secs;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number in time string: " + timeStr, e);
            }
        }

        throw new IllegalArgumentException("Time string must be in mm:ss or hh:mm:ss format: " + timeStr);
    }
}
