package com.musiclibrary.repository.db;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * SQLite database connection and lifecycle manager.
 * Manages database file location, directory initialization,
 * foreign keys enforcement, and WAL journal mode configuration.
 */
public class DatabaseManager implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(DatabaseManager.class.getName());

    public static final String DB_PATH_PROPERTY = "musiclibrary.db.path";
    public static final String DEFAULT_DB_SUBPATH = ".musiclibrary" + File.separator + "library.db";

    private final String dbPath;
    private final boolean memory;
    private volatile boolean closed = false;
    private Connection memoryConnection;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.log(System.Logger.Level.ERROR, "SQLite JDBC driver not found on classpath", e);
        }
    }

    /**
     * Initializes the database manager using system property {@code musiclibrary.db.path},
     * falling back to {@code ~/.musiclibrary/library.db} if not specified.
     */
    public DatabaseManager() {
        this(resolveDefaultDbPath());
    }

    /**
     * Initializes the database manager with an explicit path or special SQLite specifier (e.g. {@code :memory:}).
     *
     * @param dbPath the database file path or {@code :memory:}
     */
    public DatabaseManager(String dbPath) {
        this.dbPath = Objects.requireNonNull(dbPath, "dbPath must not be null");
        this.memory = isMemoryDatabase(dbPath);

        if (!this.memory) {
            initDirectories(this.dbPath);
        }
    }

    private static String resolveDefaultDbPath() {
        String sysProp = System.getProperty(DB_PATH_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, ".musiclibrary", "library.db").toAbsolutePath().toString();
    }

    private static boolean isMemoryDatabase(String path) {
        return ":memory:".equalsIgnoreCase(path.trim()) || path.contains("mode=memory");
    }

    private static void initDirectories(String dbPath) {
        try {
            Path path = Path.of(dbPath).toAbsolutePath();
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                LOGGER.log(System.Logger.Level.DEBUG, "Created database directory: {0}", parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create parent directories for database at " + dbPath, e);
        }
    }

    /**
     * Gets a connection to the database.
     * Enforces foreign key constraints (PRAGMA foreign_keys = ON) on every connection.
     * For file-based databases, sets WAL mode (PRAGMA journal_mode = WAL).
     *
     * @return an open {@link Connection}
     * @throws SQLException if connection cannot be established or configured
     */
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("DatabaseManager has already been closed");
        }

        if (memory) {
            return getMemoryConnection();
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }

        return connection;
    }

    private synchronized Connection getMemoryConnection() throws SQLException {
        if (memoryConnection == null || memoryConnection.isClosed()) {
            memoryConnection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement stmt = memoryConnection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
        }

        // Return a proxy to prevent callers' try-with-resources from destroying the shared in-memory database
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("close".equals(method.getName())) {
                            // Do not close the underlying in-memory connection
                            return null;
                        }
                        if ("isClosed".equals(method.getName())) {
                            return memoryConnection.isClosed();
                        }
                        try {
                            return method.invoke(memoryConnection, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw (e.getCause() != null) ? e.getCause() : e;
                        }
                    }
                }
        );
    }

    /**
     * Returns the database path configured for this manager.
     *
     * @return the database path or {@code :memory:}
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Checks if this DatabaseManager has been closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Releases any held database resources.
     *
     * @throws SQLException if closing held connections fails
     */
    @Override
    public synchronized void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        if (memoryConnection != null && !memoryConnection.isClosed()) {
            memoryConnection.close();
            memoryConnection = null;
        }
        LOGGER.log(System.Logger.Level.DEBUG, "DatabaseManager closed for: {0}", dbPath);
    }
}
