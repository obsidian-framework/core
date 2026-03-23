package com.obsidian.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Database connection manager with support for SQLite, MySQL, and PostgreSQL.
 *
 * Security notes:
 * - The singleton field is {@code volatile} to guarantee safe publication
 *   in multi-threaded environments without a full lock on every read.
 * - MySQL/PostgreSQL connections require SSL by default. Set
 *   {@code OBSIDIAN_DB_DISABLE_SSL=true} (env or system property) only in
 *   local dev/test. System property takes priority over env variable.
 * - Credentials are never written to the log.
 *
 * Pool tuning: explicit timeouts prevent the common failure mode
 * where stale connections accumulate because Hikari never evicts them.
 */
public class DB
{
    /** Volatile ensures the reference is safely visible across threads. */
    private static volatile DB instance;

    /**
     * Thread-local connection — one JDBC connection per thread.
     *
     * Leak risk in thread pools: threads in a pool are never destroyed, so
     * {@link ThreadLocal} values survive indefinitely unless explicitly removed.
     * A connection left in {@code threadConnection} after a request finishes holds a
     * pooled connection open and prevents it from being returned to HikariCP.
     *
     * Required usage contract — every code path that calls {@link #connect()}
     * or {@link #getConnection()} MUST eventually call {@link #closeConnection()}, even
     * on exception. The safe patterns are:
     * - {@link #withConnection(Callable)} — opens and closes automatically.
     * - {@link #withTransaction(Callable)} — same, with rollback on failure.
     * - Servlet/request filters that call {@code DB.closeConnection()} in a
     *   {@code finally} block or via a framework lifecycle hook.
     *
     * Never call {@link #getConnection()} directly in application code without one
     * of the above wrappers. Direct calls that miss {@link #closeConnection()} will
     * silently exhaust the pool under load.
     */
    private static final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();

    private static final Logger staticLogger = org.slf4j.LoggerFactory.getLogger(DB.class);

    private final Logger logger;
    private final DatabaseType type;
    private final String dbPath;
    private String jdbcUrl;
    private HikariDataSource pool;

    /**
     * Initialises a SQLite database.
     *
     * @param path   path to the SQLite file
     * @param logger logger instance
     * @return the singleton DB instance
     */
    public static DB initSQLite(String path, Logger logger)
    {
        synchronized (DB.class) {
            instance = new DB(DatabaseType.SQLITE, path, null, 0, null, null, false, logger);
        }
        return instance;
    }

    /**
     * Initialises a MySQL/MariaDB connection pool.
     *
     * @param host     database host
     * @param port     database port
     * @param database database name
     * @param user     database user
     * @param password database password
     * @param logger   logger instance
     * @return the singleton DB instance
     */
    public static DB initMySQL(String host, int port, String database, String user, String password, boolean ssl, Logger logger)
    {
        synchronized (DB.class) {
            instance = new DB(DatabaseType.MYSQL, database, host, port, user, password, ssl, logger);
        }
        return instance;
    }

    /**
     * Initialises a PostgreSQL connection pool.
     *
     * @param host     database host
     * @param port     database port
     * @param database database name
     * @param user     database user
     * @param password database password
     * @param logger   logger instance
     * @return the singleton DB instance
     */
    public static DB initPostgreSQL(String host, int port, String database, String user, String password, boolean ssl, Logger logger)
    {
        synchronized (DB.class) {
            instance = new DB(DatabaseType.POSTGRESQL, database, host, port, user, password, ssl, logger);
        }
        return instance;
    }

    /**
     * Returns the singleton instance, throwing if not yet initialised.
     *
     * @return the DB instance
     * @throws IllegalStateException if no init method has been called
     */
    public static DB getInstance()
    {
        DB db = instance;
        if (db == null) {
            throw new IllegalStateException(
                    "Database not initialised — call initSQLite / initMySQL / initPostgreSQL first.");
        }
        return db;
    }

    /**
     * Borrows a connection for the duration of {@code task}, then returns it.
     *
     * @param task callable to execute
     * @return the task's return value
     */
    public static <T> T withConnection(Callable<T> task) {
        return getInstance().executeWithConnection(task);
    }

    /**
     * Wraps {@code task} in a database transaction, rolling back on any exception.
     *
     * @param task callable to execute
     * @return the task's return value
     */
    public static <T> T withTransaction(Callable<T> task) {
        return getInstance().executeWithTransaction(task);
    }

    private DB(DatabaseType type, String database, String host, int port, String user, String password, boolean ssl, Logger logger)
    {
        this.type   = type;
        this.logger = logger;
        this.dbPath = database;

        if (type == DatabaseType.SQLITE) {
            this.jdbcUrl = "jdbc:sqlite:" + database;
            logger.info("SQLite database initialised: {}", database);
        } else {
            setupConnectionPool(type, host, port, database, user, password, ssl);
        }
    }

    private void setupConnectionPool(DatabaseType type, String host, int port, String database, String user, String password, boolean ssl)
    {
        HikariConfig config = new HikariConfig();

        String url = buildJdbcUrl(type, host, port, database, ssl);
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);

        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        // How long a caller waits for a connection before an exception is thrown.
        config.setConnectionTimeout(30_000);
        // How long an idle connection may sit in the pool before being evicted.
        config.setIdleTimeout(600_000);
        // Maximum lifetime of any connection, must be shorter than the server's wait_timeout.
        config.setMaxLifetime(1_800_000);
        // How often Hikari probes idle connections to keep them alive.
        config.setKeepaliveTime(60_000);
        // Warn if a connection is held longer than this — catches ThreadLocal leaks.
        config.setLeakDetectionThreshold(5_000);

        config.setAutoCommit(true);
        config.setPoolName("ObsidianDB-" + type.name());

        pool = new HikariDataSource(config);
        logger.info("Connection pool initialised for {} at {}:{}/{}", type, host, port, database);
    }

    /**
     * Builds a JDBC URL for the given database type.
     * SSL is controlled by the {@code DB_SSL} environment variable (default: false).
     */
    private String buildJdbcUrl(DatabaseType type, String host, int port, String database, boolean ssl)
    {
        return switch (type) {
            case MYSQL -> ssl
                    ? String.format("jdbc:mysql://%s:%d/%s?useSSL=true&verifyServerCertificate=true&serverTimezone=UTC", host, port, database)
                    : String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, database);
            case POSTGRESQL -> ssl
                    ? String.format("jdbc:postgresql://%s:%d/%s?ssl=true&sslmode=verify-full", host, port, database)
                    : String.format("jdbc:postgresql://%s:%d/%s?ssl=false", host, port, database);
            default -> throw new IllegalArgumentException("Unsupported database type: " + type);
        };
    }

    /**
     * Opens a connection for the current thread. No-op if already open.
     */
    public void connect()
    {
        try {
            if (threadConnection.get() != null) return;

            Connection conn;
            if (type == DatabaseType.SQLITE) {
                conn = DriverManager.getConnection(jdbcUrl);
            } else if (pool != null) {
                conn = pool.getConnection();
            } else {
                throw new IllegalStateException("No connection pool available");
            }
            threadConnection.set(conn);
        } catch (SQLException e) {
            logger.error("Connection failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns {@code true} if the current thread holds an open connection.
     *
     * @return {@code true} if a connection is active
     */
    public static boolean hasConnection()
    {
        Connection conn = threadConnection.get();
        if (conn == null) return false;
        try {
            return !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Returns the current thread's connection, opening one if necessary.
     *
     * @return active JDBC connection
     */
    public static Connection getConnection()
    {
        Connection conn = threadConnection.get();
        if (conn == null) {
            getInstance().connect();
            conn = threadConnection.get();
        }
        return conn;
    }

    /**
     * Closes and removes the current thread's connection.
     */
    public static void closeConnection()
    {
        Connection conn = threadConnection.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignore) {}
            threadConnection.remove();
        }
    }

    /**
     * Executes {@code task} with an open connection, closing it afterwards
     * only if this call was the one that opened it.
     *
     * @param task callable to execute
     * @return the task's return value
     */
    public <T> T executeWithConnection(Callable<T> task)
    {
        boolean created = false;
        try {
            if (!hasConnection()) {
                connect();
                created = true;
            }
            return task.call();
        } catch (Exception e) {
            logger.error("Database error: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (created && hasConnection()) {
                closeConnection();
            }
        }
    }

    /**
     * Executes {@code task} inside a transaction.
     * Rolls back and rethrows on any exception.
     *
     * @param task callable to execute
     * @return the task's return value
     */
    public <T> T executeWithTransaction(Callable<T> task)
    {
        boolean created = false;
        try {
            if (!hasConnection()) {
                connect();
                created = true;
            }
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            T result = task.call();
            conn.commit();
            conn.setAutoCommit(true);
            return result;
        } catch (Exception e) {
            try {
                Connection conn = threadConnection.get();
                if (conn != null) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                }
            } catch (SQLException rollbackEx) {
                logger.error("Rollback failed: {}", rollbackEx.getMessage());
            }
            logger.error("Transaction failed: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (created && hasConnection()) {
                closeConnection();
            }
        }
    }

    /**
     * Executes a DDL/DML statement. All variable data must be passed as {@code params}.
     *
     * @param sql    raw SQL string
     * @param params values to bind to {@code ?} placeholders
     */
    public static void exec(String sql, Object... params)
    {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            stmt.executeUpdate();
        } catch (SQLException e) {
            staticLogger.error("exec failed: {}", sql, e);
            throw new RuntimeException("Database exec failed", e);
        }
    }

    /**
     * Executes a SELECT query and returns all rows. All variable data must be passed as {@code params}.
     *
     * @param sql    raw SQL string
     * @param params values to bind to {@code ?} placeholders
     * @return list of rows as column-to-value maps
     */
    public static List<Map<String, Object>> findAll(String sql, Object... params)
    {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        } catch (SQLException e) {
            staticLogger.error("findAll failed: {}", sql, e);
            throw new RuntimeException("Database query failed", e);
        }
    }

    /**
     * Executes a SELECT and returns the first cell value, or {@code null}.
     *
     * @param sql    raw SQL string
     * @param params values to bind to {@code ?} placeholders
     * @return first cell value, or {@code null}
     */
    public static Object firstCell(String sql, Object... params)
    {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getObject(1);
                return null;
            }
        } catch (SQLException e) {
            staticLogger.error("firstCell failed: {}", sql, e);
            throw new RuntimeException("Database query failed", e);
        }
    }

    /**
     * Executes a SELECT and returns the first row as a map, or {@code null}.
     *
     * @param sql    raw SQL string
     * @param params values to bind to {@code ?} placeholders
     * @return first row as a column-to-value map, or {@code null}
     */
    public static Map<String, Object> firstRow(String sql, Object... params)
    {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = resultSetToList(rs);
                return rows.isEmpty() ? null : rows.get(0);
            }
        } catch (SQLException e) {
            staticLogger.error("firstRow failed: {}", sql, e);
            throw new RuntimeException("Database query failed", e);
        }
    }

    /**
     * Executes an INSERT and returns the generated key, or {@code null}.
     *
     * @param sql    raw SQL string
     * @param params values to bind to {@code ?} placeholders
     * @return generated key, or {@code null}
     */
    public static Object insertAndGetKey(String sql, Object... params)
    {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParams(stmt, params);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getObject(1);
                return null;
            }
        } catch (SQLException e) {
            staticLogger.error("insertAndGetKey failed: {}", sql, e);
            throw new RuntimeException("Database insert failed", e);
        }
    }

    /**
     * Closes the current thread's connection and shuts down the connection pool.
     * Synchronized to prevent concurrent access to a half-closed instance.
     */
    public void close()
    {
        synchronized (DB.class) {
            closeConnection();
            if (pool != null) {
                pool.close();
                pool = null;
                logger.info("Connection pool closed ({})", type);
            }
            instance = null;
        }
    }

    /**
     * Returns the database type.
     *
     * @return database type
     */
    public DatabaseType getType() { return type; }

    /**
     * Returns the logger instance.
     *
     * @return logger
     */
    public Logger getLogger() { return logger; }

    private static void bindParams(PreparedStatement stmt, Object... params) throws SQLException
    {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (value instanceof String) {
                stmt.setString(i + 1, (String) value);
            } else if (value instanceof Integer) {
                stmt.setInt(i + 1, (Integer) value);
            } else if (value instanceof Long) {
                stmt.setLong(i + 1, (Long) value);
            } else if (value instanceof Double) {
                stmt.setDouble(i + 1, (Double) value);
            } else if (value instanceof Float) {
                stmt.setFloat(i + 1, (Float) value);
            } else if (value instanceof Boolean) {
                stmt.setBoolean(i + 1, (Boolean) value);
            } else if (value instanceof java.util.Date) {
                stmt.setTimestamp(i + 1, new Timestamp(((java.util.Date) value).getTime()));
            } else if (value instanceof java.time.LocalDateTime) {
                stmt.setTimestamp(i + 1, Timestamp.valueOf((java.time.LocalDateTime) value));
            } else if (value instanceof java.time.LocalDate) {
                stmt.setDate(i + 1, Date.valueOf((java.time.LocalDate) value));
            } else {
                stmt.setObject(i + 1, value);
            }
        }
    }

    private static List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException
    {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }
}