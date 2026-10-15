package com.musiclibrary.repository.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaInitializerTest {

    @Test
    @DisplayName("Initializes full schema from resource and executes idempotently")
    void testInitializeSchema() throws SQLException {
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            // First run
            SchemaInitializer.initialize(dbManager);

            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Check tables exist
                String[] expectedTables = {"accounts", "tracks", "account_tracks", "playlist_folders", "playlists", "playlist_tracks"};
                for (String table : expectedTables) {
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" + table + "';")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).as("Table %s must exist", table).isEqualTo(1);
                    }
                }

                // Check default account exists
                try (ResultSet rs = stmt.executeQuery("SELECT id, name FROM accounts WHERE id = 'default';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("id")).isEqualTo("default");
                    assertThat(rs.getString("name")).isEqualTo("Default User");
                }
            }

            // Second run (idempotency check)
            assertThatCode(() -> SchemaInitializer.initialize(dbManager))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Foreign key constraints enforce referential integrity and ON DELETE CASCADE / SET NULL")
    void testForeignKeyConstraintsAndCascadeDelete() throws SQLException {
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            SchemaInitializer.initialize(dbManager);

            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Setup test data
                // 1. Account
                stmt.execute("INSERT INTO accounts (id, name, created_at, updated_at) " +
                        "VALUES ('acc-1', 'Test User 1', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");

                // 2. Tracks
                stmt.execute("INSERT INTO tracks (id, file_path, title, artist, file_last_modified, created_at, updated_at) " +
                        "VALUES ('trk-1', '/music/1.mp3', 'Song 1', 'Artist 1', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                stmt.execute("INSERT INTO tracks (id, file_path, title, artist, file_last_modified, created_at, updated_at) " +
                        "VALUES ('trk-2', '/music/2.mp3', 'Song 2', 'Artist 2', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");

                // 3. Account tracks
                stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, rating, play_count, date_added, created_at, updated_at) " +
                        "VALUES ('at-1', 'acc-1', 'trk-1', 4, 10, '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, rating, play_count, date_added, created_at, updated_at) " +
                        "VALUES ('at-2', 'acc-1', 'trk-2', 5, 20, '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");

                // 4. Folder hierarchy (parent & child)
                stmt.execute("INSERT INTO playlist_folders (id, account_id, parent_folder_id, name, created_at, updated_at) " +
                        "VALUES ('fld-parent', 'acc-1', NULL, 'Parent Folder', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                stmt.execute("INSERT INTO playlist_folders (id, account_id, parent_folder_id, name, created_at, updated_at) " +
                        "VALUES ('fld-child', 'acc-1', 'fld-parent', 'Child Folder', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");

                // 5. Playlist under parent folder
                stmt.execute("INSERT INTO playlists (id, account_id, folder_id, name, created_at, updated_at) " +
                        "VALUES ('pl-1', 'acc-1', 'fld-parent', 'My Playlist', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");

                // 6. Playlist tracks
                stmt.execute("INSERT INTO playlist_tracks (playlist_id, account_track_id, position) " +
                        "VALUES ('pl-1', 'at-1', 0);");
                stmt.execute("INSERT INTO playlist_tracks (playlist_id, account_track_id, position) " +
                        "VALUES ('pl-1', 'at-2', 1);");

                // --- Verify FK Cascade 1: Deleting track cascades to account_tracks and playlist_tracks ---
                stmt.execute("DELETE FROM tracks WHERE id = 'trk-1';");
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM account_tracks WHERE id = 'at-1';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("account_track at-1 should be cascaded when trk-1 is deleted").isEqualTo(0);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM playlist_tracks WHERE account_track_id = 'at-1';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("playlist_track for at-1 should be cascaded").isEqualTo(0);
                }
                // Verify trk-2 is still intact
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM account_tracks WHERE id = 'at-2';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }

                // --- Verify FK Cascade 2: Deleting parent folder cascades to child folder, and sets playlists.folder_id to NULL ---
                stmt.execute("DELETE FROM playlist_folders WHERE id = 'fld-parent';");
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM playlist_folders WHERE id = 'fld-child';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Child folder should be cascaded when parent is deleted").isEqualTo(0);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT folder_id FROM playlists WHERE id = 'pl-1';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("folder_id")).as("folder_id should be SET NULL on folder delete").isNull();
                }

                // --- Verify FK Cascade 3: Deleting account cascades to all user data ---
                stmt.execute("DELETE FROM accounts WHERE id = 'acc-1';");
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM account_tracks WHERE account_id = 'acc-1';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("account_tracks should be cascaded on account delete").isEqualTo(0);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM playlists WHERE account_id = 'acc-1';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("playlists should be cascaded on account delete").isEqualTo(0);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM playlist_tracks WHERE playlist_id = 'pl-1';")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("playlist_tracks should be cascaded on playlist/account delete").isEqualTo(0);
                }

                // --- Verify FK Rejection: Inserting child with non-existent foreign key throws SQLException ---
                assertThatThrownBy(() -> stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, date_added, created_at, updated_at) " +
                        "VALUES ('at-err', 'non-existent-acc', 'trk-2', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');"))
                        .isInstanceOf(SQLException.class);

                assertThatThrownBy(() -> stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, date_added, created_at, updated_at) " +
                        "VALUES ('at-err', 'default', 'non-existent-track', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("Unique constraints prevent duplicate account names, track file paths, account track pairs, and playlist positions")
    void testUniqueConstraints() throws SQLException {
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            SchemaInitializer.initialize(dbManager);

            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement()) {

                // 1. accounts.name UNIQUE
                stmt.execute("INSERT INTO accounts (id, name, created_at, updated_at) " +
                        "VALUES ('user-a', 'MusicLover', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                assertThatThrownBy(() -> stmt.execute("INSERT INTO accounts (id, name, created_at, updated_at) " +
                        "VALUES ('user-b', 'MusicLover', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');"))
                        .isInstanceOf(SQLException.class);

                // 2. tracks.file_path UNIQUE
                stmt.execute("INSERT INTO tracks (id, file_path, file_last_modified, created_at, updated_at) " +
                        "VALUES ('t-1', '/music/unique.mp3', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                assertThatThrownBy(() -> stmt.execute("INSERT INTO tracks (id, file_path, file_last_modified, created_at, updated_at) " +
                        "VALUES ('t-2', '/music/unique.mp3', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');"))
                        .isInstanceOf(SQLException.class);

                // 3. account_tracks UNIQUE (account_id, track_id)
                stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, date_added, created_at, updated_at) " +
                        "VALUES ('at-u1', 'user-a', 't-1', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                assertThatThrownBy(() -> stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, date_added, created_at, updated_at) " +
                        "VALUES ('at-u2', 'user-a', 't-1', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');"))
                        .isInstanceOf(SQLException.class);

                // 4. playlist_tracks PRIMARY KEY (playlist_id, position)
                stmt.execute("INSERT INTO playlists (id, account_id, name, created_at, updated_at) " +
                        "VALUES ('pl-u', 'user-a', 'Favorites', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                stmt.execute("INSERT INTO playlist_tracks (playlist_id, account_track_id, position) " +
                        "VALUES ('pl-u', 'at-u1', 0);");
                assertThatThrownBy(() -> stmt.execute("INSERT INTO playlist_tracks (playlist_id, account_track_id, position) " +
                        "VALUES ('pl-u', 'at-u1', 0);"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("CHECK constraints reject out-of-range rating (<0 or >5) and negative play_count")
    void testCheckConstraints() throws SQLException {
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            SchemaInitializer.initialize(dbManager);

            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.execute("INSERT INTO tracks (id, file_path, file_last_modified, created_at, updated_at) " +
                        "VALUES ('trk-chk', '/music/check.mp3', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");

                // Valid boundary values: rating 0 and 5, play_count 0 and 100 -> succeed
                stmt.execute("INSERT INTO account_tracks (id, account_id, track_id, rating, play_count, date_added, created_at, updated_at) " +
                        "VALUES ('at-valid-0', 'default', 'trk-chk', 0, 0, '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z', '2026-10-15T00:00:00Z');");
                stmt.execute("UPDATE account_tracks SET rating = 5, play_count = 100 WHERE id = 'at-valid-0';");

                // Invalid rating < 0
                assertThatThrownBy(() -> stmt.execute("UPDATE account_tracks SET rating = -1 WHERE id = 'at-valid-0';"))
                        .isInstanceOf(SQLException.class);

                // Invalid rating > 5
                assertThatThrownBy(() -> stmt.execute("UPDATE account_tracks SET rating = 6 WHERE id = 'at-valid-0';"))
                        .isInstanceOf(SQLException.class);

                // Invalid play_count < 0
                assertThatThrownBy(() -> stmt.execute("UPDATE account_tracks SET play_count = -1 WHERE id = 'at-valid-0';"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("SchemaInitializer handles exceptions on null inputs and closed connections")
    void testSchemaInitializerExceptions() throws SQLException {
        // 1. null arguments
        assertThatThrownBy(() -> SchemaInitializer.initialize((DatabaseManager) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dbManager");

        assertThatThrownBy(() -> SchemaInitializer.initialize((Connection) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("connection");

        // 2. closed connection
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            Connection conn = dbManager.getConnection();
            dbManager.close(); // Underlying or proxy closed

            assertThatThrownBy(() -> SchemaInitializer.initialize(dbManager))
                    .isInstanceOf(SQLException.class);
        }

        // 3. invalid SQL execution throws SQLException
        try (DatabaseManager dbManager = new DatabaseManager(":memory:");
             Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            assertThatThrownBy(() -> stmt.execute("THIS IS NOT VALID SQL;"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("parseStatements correctly parses statements separated by semicolons and trims whitespace")
    void testParseStatements() {
        String script = "CREATE TABLE t1 (id INT);\n" +
                "CREATE TABLE t2 (name TEXT);\n" +
                ";;\n" +
                "   \n";

        java.util.List<String> statements = SchemaInitializer.parseStatements(script);
        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).isEqualTo("CREATE TABLE t1 (id INT)");
        assertThat(statements.get(1)).isEqualTo("CREATE TABLE t2 (name TEXT)");
    }
}
