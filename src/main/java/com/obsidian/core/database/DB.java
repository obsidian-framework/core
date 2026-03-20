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
 * <p><b>Security notes</b>:</p>
 * <ul>
 *   <li>The singleton field is {@code volatile} to guarantee safe publication
 *       in multi-threaded environments without a full lock on every read.</li>
 *   <li>MySQL/PostgreSQL connections require SSL by default. Set
 *       {@code OBSIDIAN_DB_DISABLE_SSL=true} (env or system property) only in
 *       local dev/test. System property takes priority over env variable.</li>
 *   <li>Credentials are never written to the log.</li>
 * </ul>
 *
 * <p><b>Pool tuning</b>: explicit timeouts prevent the common failure mode
 * where stale connections accumulate because Hikari never evicts them.</p>
 */
public class DB
{
    /** Volatile ensures the reference is safely visible across threads. */
    private static volatile DB instance;

    /**
     * Thread-local connection — one JDBC connection per thread.
     *
     * <p><b>Leak risk in thread pools</b>: threads in a pool are never destroyed, so
     * {@link ThreadLocal} values survive indefinitely unless explicitly removed.
     * A connection left in {@code threadConnection} after a request finishes holds a
     * pooled connection open and prevents it from being returned to HikariCP.</p>
     *
     * <p><b>Required usage contract</b> — every code path that calls {@link #connect()}
     * or {@link #getConnection()} MUST eventually call {@link #closeConnection()}, even
     * on exception. The safe patterns are:</p>
     * <ul>
     *   <li>{@link #withConnection(Callable)} — opens and closes automatically.</li>
     *   <li>{@link #withTransaction(Callable)} — same, with rollback on failure.</li>
     *   <li>Servlet/request filters that call {@code DB.closeConnection()} in a
     *       {@code finally} block or via a framework lifecycle hook.</li>
     * </ul>
     *
     * <p>Never call {@link #getConnection()} directly in application code without one
     * of the above wrappers. Direct calls that miss {@link #closeConnection()} will
     * silently exhaust the pool under load.</p>
     */
    private static final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();

    /** Logger instance */
    private final Logger logger;

    /** Database type */
    private final DatabaseType type;

    /** Database path (SQLite) or database name (MySQL/PostgreSQL) */
    private final String dbPath;

    /** JDBC URL for SQLite */
    private String jdbcUrl;

    /** Connection pool for MySQL/PostgreSQL */
    private HikariDataSource pool;

    // ─── STATIC FACTORY METHODS ──────────────────────────────

    /**
     * Initialises a SQLite database.
     *
     * @param path   Path to the SQLite file
     * @param logger Logger instance
     * @return The singleton DB instance
     */
    public static DB initSQLite(String path, Logger logger) {
        synchronized (DB.class) {
            instance = new DB(DatabaseType.SQLITE, path, null, 0, null, null, logger);
        }
        return instance;
    }

    /**
     * Initialises a MySQL/MariaDB connection pool.
     *
     * @param host     Database host
     * @param port     Database port
     * @param database Database name
     * @param user     Database user
     * @param password Database password
     * @param logger   Logger instance
     * @return The singleton DB instance
     */
    public static DB initMySQL(String host, int port, String database,
                               String user, String password, Logger logger) {
        synchronized (DB.class) {
            instance = new DB(DatabaseType.MYSQL, database, host, port, user, password, logger);
        }
        return instance;
    }

    /**
     * Initialises a PostgreSQL connection pool.
     *
     * @param host     Database host
     * @param port     Database port
     * @param database Database name
     * @param user     Database user
     * @param password Database password
     * @param logger   Logger instance
     * @return The singleton DB instance
     */
    public static DB initPostgreSQL(String host, int port, String database,
                                    String user, String password, Logger logger) {
        synchronized (DB.class) {
            instance = new DB(DatabaseType.POSTGRESQL, database, host, port, user, password, logger);
        }
        return instance;
    }

    /**
     * Returns the singleton instance, throwing if not yet initialised.
     *
     * @return The DB instance
     */
    public static DB getInstance() {
        DB db = instance; // single volatile read
        if (db == null) {
            throw new IllegalStateException(
                    "Database not initialised — call initSQLite / initMySQL / initPostgreSQL first.");
        }
        return db;
    }

    // ─── STATIC CONVENIENCE METHODS ──────────────────────────

    /**
     * Borrows a connection for the duration of {@code task}, then returns it.
     *
     * @param task The task
     * @return The task's return value
     */
    public static <T> T withConnection(Callable<T> task) {
        return getInstance().executeWithConnection(task);
    }

    /**
     * Wraps {@code task} in a database transaction, rolling back on any exception.
     *
     * @param task The task
     * @return The task's return value
     */
    public static <T> T withTransaction(Callable<T> task) {
        return getInstance().executeWithTransaction(task);
    }

    // ─── CONSTRUCTOR ─────────────────────────────────────────

    private DB(DatabaseType type, String database, String host, int port,
               String user, String password, Logger logger)
    {
        this.type   = type;
        this.logger = logger;
        this.dbPath = database;

        if (type == DatabaseType.SQLITE) {
            this.jdbcUrl = "jdbc:sqlite:" + database;
            logger.info("SQLite database initialised: {}", database);
        } else {
            setupConnectionPool(type, host, port, database, user, password);
        }
    }

    private void setupConnectionPool(DatabaseType type, String host, int port,
                                     String database, String user, String password)
    {
        HikariConfig config = new HikariConfig();

        String url = buildJdbcUrl(type, host, port, database);
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);

        // Pool sizing
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        // ── Timeouts ────────────────────────────────────────
        // How long a caller waits for a connection before an exception is thrown.
        config.setConnectionTimeout(30_000);        // 30 s
        // How long an idle connection may sit in the pool before being evicted.
        config.setIdleTimeout(600_000);             // 10 min
        // Maximum lifetime of any connection in the pool, regardless of activity.
        // Must be shorter than the server's wait_timeout to avoid "Connection reset" errors.
        config.setMaxLifetime(1_800_000);           // 30 min
        // How often Hikari probes idle connections to keep them alive.
        config.setKeepaliveTime(60_000);            // 1 min
        // Warn if a connection is held for longer than this — catches ThreadLocal leaks
        // where closeConnection() was never called after a request. Set to 0 to disable.
        config.setLeakDetectionThreshold(5_000);    // 5 s

        config.setAutoCommit(true);
        config.setPoolName("ObsidianDB-" + type.name());

        pool = new HikariDataSource(config);
        // Log host+database but NOT credentials
        logger.info("Connection pool initialised for {} at {}:{}/{}", type, host, port, database);
    }

    /**
     * Builds a JDBC URL for the given database type.
     *
     * <p>SSL is enabled for both MySQL and PostgreSQL by default.
     * Disable only for local dev/test by setting {@code OBSIDIAN_DB_DISABLE_SSL=true}
     * as an environment variable OR as a JVM system property ({@code -DOBSIDIAN_DB_DISABLE_SSL=true}).
     * System property takes priority — this allows {@code exec-maven-plugin} to pass the flag
     * via {@code <systemProperties>} without relying on OS-level env injection, which does not
     * work when Maven runs in-process.</p>
     */
    private String buildJdbcUrl(DatabaseType type, String host, int port, String database) {
        boolean disableSsl = isSslDisabled();
        return switch (type) {
            case MYSQL -> {
                if (disableSsl) {
                    logger.warn("MySQL SSL verification is DISABLED (OBSIDIAN_DB_DISABLE_SSL=true). " +
                            "Do not use this setting in production.");
                    yield String.format(
                            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                            host, port, database);
                }
                yield String.format(
                        "jdbc:mysql://%s:%d/%s?useSSL=true&verifyServerCertificate=true&serverTimezone=UTC",
                        host, port, database);
            }
            case POSTGRESQL -> {
                if (disableSsl) {
                    logger.warn("PostgreSQL SSL verification is DISABLED (OBSIDIAN_DB_DISABLE_SSL=true). " +
                            "Do not use this setting in production.");
                    yield String.format("jdbc:postgresql://%s:%d/%s?ssl=false", host, port, database);
                }
                // sslmode=verify-full requires the server certificate to match the hostname
                // and be signed by a trusted CA — equivalent to MySQL verifyServerCertificate=true.
                yield String.format(
                        "jdbc:postgresql://%s:%d/%s?ssl=true&sslmode=verify-full",
                        host, port, database);
            }
            default -> throw new IllegalArgumentException("Unsupported database type: " + type);
        };
    }

    /**
     * Returns true if SSL should be disabled.
     *
     * <p>Checks system property first (set via {@code -D} or {@code exec-maven-plugin}
     * {@code <systemProperties>}), then falls back to the OS environment variable.
     * System property wins because {@code exec:java} runs in-process and cannot inject
     * environment variables after JVM startup.</p>
     */
    private boolean isSslDisabled() {
        String sysProp = System.getProperty("OBSIDIAN_DB_DISABLE_SSL");
        if (sysProp != null) return "true".equalsIgnoreCase(sysProp);
        return "true".equalsIgnoreCase(System.getenv("OBSIDIAN_DB_DISABLE_SSL"));
    }

    // ─── CONNECTION MANAGEMENT ───────────────────────────────

    /**
     * Opens a connection for the current thread (no-op if already open).
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
     * Returns true if the current thread holds an open connection.
     */
    public static boolean hasConnection() {
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
     */
    public static Connection getConnection() {
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
    public static void closeConnection() {
        Connection conn = threadConnection.get();
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignore) {}
            threadConnection.remove();
        }
    }

    // ─── EXECUTE WITH CONNECTION / TRANSACTION ───────────────

    /**
     * Executes {@code task} with an open connection, closing it afterwards
     * only if this call was the one that opened it.
     *
     * @param task The task
     * @return The task's return value
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
     * @param task The task
     * @return The task's return value
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

    // ─── RAW SQL EXECUTION ───────────────────────────────────

    /**
     * Executes a DDL/DML statement (CREATE, INSERT, UPDATE, DELETE).
     * All variable data must be passed as {@code params} — never interpolated.
     */
    public static void exec(String sql, Object... params) {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL exec failed: " + sql, e);
        }
    }

    /**
     * Executes a SELECT query and returns a list of rows.
     * All variable data must be passed as {@code params} — never interpolated.
     */
    public static List<Map<String, Object>> findAll(String sql, Object... params) {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL query failed: " + sql, e);
        }
    }

    /**
     * Executes a SELECT and returns the first cell value, or {@code null}.
     */
    public static Object firstCell(String sql, Object... params) {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject(1);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL firstCell failed: " + sql, e);
        }
    }

    /**
     * Executes a SELECT and returns the first row as a map, or {@code null}.
     */
    public static Map<String, Object> firstRow(String sql, Object... params) {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> rows = resultSetToList(rs);
                return rows.isEmpty() ? null : rows.get(0);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL firstRow failed: " + sql, e);
        }
    }

    /**
     * Executes an INSERT and returns the generated key, or {@code null}.
     */
    public static Object insertAndGetKey(String sql, Object... params) {
        Connection conn = getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParams(stmt, params);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getObject(1);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL insert failed: " + sql, e);
        }
    }

    // ─── CLOSE / SHUTDOWN ────────────────────────────────────

    /**
     * Closes the current thread's connection and shuts down the pool.
     *
     * <p>Synchronized on {@code DB.class} for the same reason as the {@code init*} methods:
     * a thread calling {@code close()} concurrently with another thread reading {@code instance}
     * could observe a non-null instance whose pool has already been shut down. The lock ensures
     * that once {@code close()} completes, any subsequent {@code getInstance()} either gets
     * a valid instance or throws — never a half-closed one.</p>
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

    // ─── ACCESSORS ───────────────────────────────────────────

    /**
     * Returns the database type.
     *
     * @return The database type
     */
    public DatabaseType getType() { return type; }

    /**
     * Returns the logger.
     *
     * @return The logger
     */
    public Logger getLogger() { return logger; }

    // ─── INTERNAL HELPERS ────────────────────────────────────

    private static void bindParams(PreparedStatement stmt, Object... params) throws SQLException {
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