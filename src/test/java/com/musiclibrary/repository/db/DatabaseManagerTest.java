package com.musiclibrary.repository.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("In-memory database creates shared connection and enforces foreign keys")
    void testInMemoryDatabase() throws SQLException {
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            assertThat(dbManager.getDbPath()).isEqualTo(":memory:");

            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1); // foreign_keys = 1 (ON)
            }

            // Verify connection remains open across calls for in-memory DB
            try (Connection conn1 = dbManager.getConnection();
                 Statement stmt = conn1.createStatement()) {
                stmt.execute("CREATE TABLE test_tab (id INT PRIMARY KEY);");
            }

            try (Connection conn2 = dbManager.getConnection();
                 Statement stmt = conn2.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM test_tab;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("File-based database creates parent directory and sets WAL mode")
    void testFileBasedDatabase() throws SQLException {
        Path dbFile = tempDir.resolve("subfolder").resolve("test.db");
        assertThat(Files.exists(dbFile.getParent())).isFalse();

        try (DatabaseManager dbManager = new DatabaseManager(dbFile.toString())) {
            assertThat(Files.exists(dbFile.getParent())).isTrue();

            try (Connection conn = dbManager.getConnection();
                 Statement stmt = conn.createStatement()) {

                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }

                try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
                }
            }
        }
    }

    @Test
    @DisplayName("Closed DatabaseManager throws SQLException on getConnection")
    void testClosedDatabaseManager() throws SQLException {
        DatabaseManager dbManager = new DatabaseManager(":memory:");
        dbManager.close();

        assertThatThrownBy(dbManager::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("Default constructor uses musiclibrary.db.path property")
    void testDefaultConstructorSystemProperty() {
        String originalProp = System.getProperty(DatabaseManager.DB_PATH_PROPERTY);
        Path customDb = tempDir.resolve("custom_sys.db");
        try {
            System.setProperty(DatabaseManager.DB_PATH_PROPERTY, customDb.toString());
            DatabaseManager dbManager = new DatabaseManager();
            assertThat(dbManager.getDbPath()).isEqualTo(customDb.toString());
        } finally {
            if (originalProp != null) {
                System.setProperty(DatabaseManager.DB_PATH_PROPERTY, originalProp);
            } else {
                System.clearProperty(DatabaseManager.DB_PATH_PROPERTY);
            }
        }
    }

    @Test
    @DisplayName("Constructor rejects null dbPath with NullPointerException")
    void testConstructorNullValidation() {
        assertThatThrownBy(() -> new DatabaseManager(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dbPath");
    }

    @Test
    @DisplayName("isClosed returns false initially and true after close")
    void testIsClosedLifecycle() throws SQLException {
        DatabaseManager dbManager = new DatabaseManager(":memory:");
        assertThat(dbManager.isClosed()).isFalse();

        dbManager.close();
        assertThat(dbManager.isClosed()).isTrue();

        // Idempotent close
        dbManager.close();
        assertThat(dbManager.isClosed()).isTrue();
    }

    @Test
    @DisplayName("In-memory database connection proxy prevents premature close and delegates isClosed")
    void testMemoryDatabaseConnectionProxy() throws SQLException {
        try (DatabaseManager dbManager = new DatabaseManager(":memory:")) {
            Connection conn = dbManager.getConnection();
            assertThat(conn.isClosed()).isFalse();

            // Calling close() on the proxy should NOT close underlying connection
            conn.close();
            assertThat(conn.isClosed()).isFalse();

            // Operations still succeed
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
