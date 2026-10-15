package com.musiclibrary.service.storage;

import java.nio.file.Path;

/**
 * Result representation of a data migration from JSON to SQLite.
 */
public class MigrationResult {

    private final boolean success;
    private final boolean skipped;
    private final int tracksMigrated;
    private final int playlistsMigrated;
    private final Path backupPath;
    private final String message;
    private final Throwable error;

    public MigrationResult(boolean success, boolean skipped, int tracksMigrated, int playlistsMigrated,
                           Path backupPath, String message, Throwable error) {
        this.success = success;
        this.skipped = skipped;
        this.tracksMigrated = tracksMigrated;
        this.playlistsMigrated = playlistsMigrated;
        this.backupPath = backupPath;
        this.message = message;
        this.error = error;
    }

    public static MigrationResult success(int tracks, int playlists, Path backupPath) {
        String msg = String.format("Migration succeeded: %d tracks, %d playlists migrated. Backup saved to %s",
                tracks, playlists, backupPath != null ? backupPath.getFileName() : "none");
        return new MigrationResult(true, false, tracks, playlists, backupPath, msg, null);
    }

    public static MigrationResult skipped(String reason) {
        return new MigrationResult(true, true, 0, 0, null, reason != null ? reason : "Migration skipped", null);
    }

    public static MigrationResult failure(String message, Throwable error) {
        return new MigrationResult(false, false, 0, 0, null, message, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public int getTracksMigrated() {
        return tracksMigrated;
    }

    public int getPlaylistsMigrated() {
        return playlistsMigrated;
    }

    public Path getBackupPath() {
        return backupPath;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "MigrationResult{" +
                "success=" + success +
                ", skipped=" + skipped +
                ", tracksMigrated=" + tracksMigrated +
                ", playlistsMigrated=" + playlistsMigrated +
                ", backupPath=" + backupPath +
                ", message='" + message + '\'' +
                ", error=" + error +
                '}';
    }
}
