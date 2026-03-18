package com.obsidian.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.javalite.activejdbc.Base;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

/**
 * Database connection manager with support for SQLite, MySQL and PostgreSQL.
 * Provides connection pooling for MySQL/PostgreSQL and transaction management.
 */
public class DB
{
    /** Singleton instance */
    private static DB instance;

    /** Logger instance */
    private final Logger logger;

    /** Database type */
    private final DatabaseType type;

    /** Database path (for SQLite) or name (for MySQL/PostgreSQL) */
    private final String dbPath;

    /** Connection pool for MySQL/PostgreSQL */
    private HikariDataSource pool;

    /**
     * Initializes SQLite database.
     *
     * @param path Path to SQLite database file
     * @param logger Logger instance
     * @return DB instance
     */
    public static DB initSQLite(String path, Logger logger) {
        instance = new DB(DatabaseType.SQLITE, path, null, 0, null, null, logger);
        return instance;
    }

    /**
     * Initializes MySQL database with connection pooling.
     *
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param user Database user
     * @param password Database password
     * @param logger Logger instance
     * @return DB instance
     */
    public static DB initMySQL(String host, int port, String database, String user, String password, Logger logger) {
        instance = new DB(DatabaseType.MYSQL, database, host, port, user, password, logger);
        return instance;
    }

    /**
     * Initializes PostgreSQL database with connection pooling.
     *
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param user Database user
     * @param password Database password
     * @param logger Logger instance
     * @return DB instance
     */
    public static DB initPostgreSQL(String host, int port, String database, String user, String password, Logger logger) {
        instance = new DB(DatabaseType.POSTGRESQL, database, host, port, user, password, logger);
        return instance;
    }

    /**
     * Gets the singleton DB instance.
     *
     * @return DB instance
     * @throws IllegalStateException if database not initialized
     */
    public static DB getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Database not initialized!");
        }
        return instance;
    }

    /**
     * Executes a task with database connection.
     * Static convenience method.
     *
     * @param task Task to execute
     * @param <T> Return type
     * @return Task result
     */
    public static <T> T withConnection(Callable<T> task) {
        return getInstance().executeWithConnection(task);
    }

    /**
     * Executes a task within a transaction.
     * Static convenience method.
     *
     * @param task Task to execute
     * @param <T> Return type
     * @return Task result
     */
    public static <T> T withTransaction(Callable<T> task) {
        return getInstance().executeWithTransaction(task);
    }

    /**
     * Private constructor.
     * Initializes database connection or connection pool.
     *
     * @param type Database type
     * @param database Database path/name
     * @param host Database host (null for SQLite)
     * @param port Database port (0 for SQLite)
     * @param user Database user (null for SQLite)
     * @param password Database password (null for SQLite)
     * @param logger Logger instance
     */
    private DB(DatabaseType type, String database, String host, int port, String user, String password, Logger logger)
    {
        this.type = type;
        this.logger = logger;
        this.dbPath = database;

        if (type == DatabaseType.SQLITE) {
            logger.info("SQLite database initialized: " + database);
        } else {
            setupConnectionPool(type, host, port, database, user, password);
        }
    }

    /**
     * Sets up HikariCP connection pool for MySQL/PostgreSQL.
     *
     * @param type Database type
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param user Database user
     * @param password Database password
     */
    private void setupConnectionPool(DatabaseType type, String host, int port, String database, String user, String password)
    {
        HikariConfig config = new HikariConfig();

        String url = switch (type) {
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false", host, port, database);
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };

        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        pool = new HikariDataSource(config);
        logger.info("Connection pool initialized for " + type);
    }

    /**
     * Executes a task with database connection.
     * Opens connection if needed, closes it after execution.
     *
     * @param task Task to execute
     * @param <T> Return type
     * @return Task result
     */
    public <T> T executeWithConnection(Callable<T> task)
    {
        boolean created = false;
        try {
            if (!Base.hasConnection()) {
                connect();
                created = true;
            }
            return task.call();
        } catch (Exception e) {
            logger.error("Database error: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (created && Base.hasConnection()) {
                Base.close();
            }
        }
    }

    /**
     * Executes a task within a transaction.
     * Commits on success, rolls back on failure.
     *
     * @param task Task to execute
     * @param <T> Return type
     * @return Task result
     */
    public <T> T executeWithTransaction(Callable<T> task)
    {
        boolean created = false;
        try {
            if (!Base.hasConnection()) {
                connect();
                created = true;
            }
            Base.openTransaction();
            T result = task.call();
            Base.commitTransaction();
            return result;
        } catch (Exception e) {
            if (Base.hasConnection()) {
                Base.rollbackTransaction();
            }
            logger.error("Transaction failed: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            if (created && Base.hasConnection()) {
                Base.close();
            }
        }
    }

    /**
     * Closes database connection and connection pool.
     */
    public void close()
    {
        if (Base.hasConnection()) {
            Base.close();
        }
        if (pool != null) {
            pool.close();
            logger.info("Connection pool closed");
        }
    }

    /**
     * Gets database type.
     *
     * @return Database type
     */
    public DatabaseType getType() {
        return type;
    }

    /**
     * Opens database connection.
     * Uses JDBC for SQLite, connection pool for MySQL/PostgreSQL.
     */
    public void connect()
    {
        try {
            if (type == DatabaseType.SQLITE) {
                String url = "jdbc:sqlite:" + dbPath;
                Base.open("org.sqlite.JDBC", url, "", "");
            } else if (pool != null) {
                Base.open(pool);
            }
        } catch (Exception e) {
            logger.error("Connection failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}