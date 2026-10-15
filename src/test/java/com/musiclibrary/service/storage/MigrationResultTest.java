package com.musiclibrary.service.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationResultTest {

    @Test
    @DisplayName("success factory creates expected success result")
    void testSuccessResult() {
        Path backup = Path.of("library.json.bak");
        MigrationResult result = MigrationResult.success(10, 2, backup);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSkipped()).isFalse();
        assertThat(result.getTracksMigrated()).isEqualTo(10);
        assertThat(result.getPlaylistsMigrated()).isEqualTo(2);
        assertThat(result.getBackupPath()).isEqualTo(backup);
        assertThat(result.getMessage()).contains("10 tracks, 2 playlists migrated");
        assertThat(result.getError()).isNull();
        assertThat(result.toString()).contains("success=true", "tracksMigrated=10");
    }

    @Test
    @DisplayName("skipped factory creates expected skipped result")
    void testSkippedResult() {
        MigrationResult result = MigrationResult.skipped("Already up to date");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSkipped()).isTrue();
        assertThat(result.getTracksMigrated()).isEqualTo(0);
        assertThat(result.getPlaylistsMigrated()).isEqualTo(0);
        assertThat(result.getBackupPath()).isNull();
        assertThat(result.getMessage()).isEqualTo("Already up to date");
        assertThat(result.getError()).isNull();

        MigrationResult defaultSkipped = MigrationResult.skipped(null);
        assertThat(defaultSkipped.getMessage()).isEqualTo("Migration skipped");
    }

    @Test
    @DisplayName("failure factory creates expected failure result")
    void testFailureResult() {
        Exception cause = new RuntimeException("IO Failure");
        MigrationResult result = MigrationResult.failure("Failed to read", cause);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isSkipped()).isFalse();
        assertThat(result.getTracksMigrated()).isEqualTo(0);
        assertThat(result.getPlaylistsMigrated()).isEqualTo(0);
        assertThat(result.getBackupPath()).isNull();
        assertThat(result.getMessage()).isEqualTo("Failed to read");
        assertThat(result.getError()).isEqualTo(cause);
    }
}
