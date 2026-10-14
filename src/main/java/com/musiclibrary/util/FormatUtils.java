package com.musiclibrary.util;

import java.util.Locale;

/**
 * Common formatting utility methods for file sizes, numbers, and audio metrics.
 */
public final class FormatUtils {

    public static final long BYTES_PER_KB = 1024L;
    public static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    public static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;

    private FormatUtils() {
        // Prevent instantiation
    }

    /**
     * Formats byte count into a human-readable representation (B, KB, MB, GB).
     * Non-positive values return "0 B".
     *
     * @param bytes byte count to format
     * @return formatted size string (e.g., "0 B", "500 B", "50.0 KB", "100.0 MB", "2.0 GB")
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        double kb = (double) bytes / BYTES_PER_KB;
        double mb = (double) bytes / BYTES_PER_MB;
        double gb = (double) bytes / BYTES_PER_GB;

        if (gb >= 1.0) {
            return String.format(Locale.US, "%.1f GB", gb);
        } else if (mb >= 1.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        } else if (kb >= 1.0) {
            return String.format(Locale.US, "%.1f KB", kb);
        } else {
            return bytes + " B";
        }
    }
}
