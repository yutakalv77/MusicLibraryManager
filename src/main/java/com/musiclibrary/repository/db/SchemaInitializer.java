package com.musiclibrary.repository.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class to initialize the SQLite database schema from resource DDL (/schema.sql).
 */
public final class SchemaInitializer {

    private static final System.Logger LOGGER = System.getLogger(SchemaInitializer.class.getName());
    public static final String SCHEMA_RESOURCE_PATH = "/schema.sql";

    private SchemaInitializer() {
        // Utility class
    }

    /**
     * Initializes the database schema using a DatabaseManager.
     *
     * @param dbManager the database manager to obtain a connection from
     * @throws SQLException if a database access error occurs or schema file cannot be processed
     */
    public static void initialize(DatabaseManager dbManager) throws SQLException {
        Objects.requireNonNull(dbManager, "dbManager must not be null");
        try (Connection conn = dbManager.getConnection()) {
            initialize(conn);
        }
    }

    /**
     * Initializes the database schema on the given connection.
     * Reads /schema.sql from the classpath, strips line comments starting with '--',
     * splits by semicolons, and executes statements sequentially.
     *
     * @param connection the target connection
     * @throws SQLException if an error occurs reading the schema or executing SQL statements
     */
    public static void initialize(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection must not be null");

        LOGGER.log(System.Logger.Level.INFO, "Starting database schema initialization from {0}...", SCHEMA_RESOURCE_PATH);

        String sqlScript = loadSchemaScript();
        List<String> statements = parseStatements(sqlScript);

        try (Statement stmt = connection.createStatement()) {
            for (String sql : statements) {
                LOGGER.log(System.Logger.Level.DEBUG, "Executing schema SQL statement: {0}", sql);
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Failed to execute schema SQL: " + e.getMessage(), e);
            throw e;
        }

        LOGGER.log(System.Logger.Level.INFO, "Database schema initialized successfully ({0} statements executed).", statements.size());
    }

    private static String loadSchemaScript() throws SQLException {
        try (InputStream in = SchemaInitializer.class.getResourceAsStream(SCHEMA_RESOURCE_PATH)) {
            if (in == null) {
                throw new SQLException("Schema definition resource not found on classpath: " + SCHEMA_RESOURCE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    // Strip comment lines starting with '--'
                    if (trimmed.startsWith("--")) {
                        continue;
                    }
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read schema resource: " + SCHEMA_RESOURCE_PATH, e);
        }
    }

    static List<String> parseStatements(String sqlScript) {
        List<String> list = new ArrayList<>();
        String[] rawStatements = sqlScript.split(";");
        for (String raw : rawStatements) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }
}
