package com.obsidian.core.database;

import com.obsidian.core.core.EnvKeys;
import com.obsidian.core.core.EnvLoader;
import com.obsidian.core.core.Obsidian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database connection loader.
 * Initializes the database connection based on environment configuration.
 * Supports SQLite, MySQL, and PostgreSQL.
 */
public class DatabaseLoader
{
    public final static Logger logger = LoggerFactory.getLogger(DatabaseLoader.class);

    /**
     * Reads environment configuration and initializes the appropriate database connection.
     */
    public static void loadDatabase()
    {
        logger.info("Loading database...");
        EnvLoader env = Obsidian.loadConfigAndEnv();

        DatabaseType dbType = DatabaseType.fromString(env.get(EnvKeys.DB_TYPE));

        switch (dbType)
        {
            case SQLITE:
                String dbPath = env.get(EnvKeys.DB_PATH);
                if (dbPath == null || dbPath.isEmpty()) {
                    dbPath = "data.db";
                }
                DB.initSQLite(dbPath, logger);
                break;
            case MYSQL:
                DB.initMySQL(
                        resolveHost(env, "localhost"),
                        resolvePort(env, 3306),
                        requireEnv(env, EnvKeys.DB_NAME, "DB_NAME"),
                        requireEnv(env, EnvKeys.DB_USER, "DB_USER"),
                        requireEnv(env, EnvKeys.DB_PASSWORD, "DB_PASSWORD"),
                        logger
                );
                break;
            case POSTGRESQL:
                DB.initPostgreSQL(
                        resolveHost(env, "localhost"),
                        resolvePort(env, 5432),
                        requireEnv(env, EnvKeys.DB_NAME, "DB_NAME"),
                        requireEnv(env, EnvKeys.DB_USER, "DB_USER"),
                        requireEnv(env, EnvKeys.DB_PASSWORD, "DB_PASSWORD"),
                        logger
                );
                break;
        }
    }

    private static String resolveHost(EnvLoader env, String defaultHost) {
        String host = env.get(EnvKeys.DB_HOST);
        return (host != null && !host.isEmpty()) ? host : defaultHost;
    }

    private static int resolvePort(EnvLoader env, int defaultPort)
    {
        String port = env.get(EnvKeys.DB_PORT);
        if (port == null || port.isEmpty()) return defaultPort;
        try {
            return Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid DB_PORT value '{}', using default {}", port, defaultPort);
            return defaultPort;
        }
    }

    private static String requireEnv(EnvLoader env, String key, String label)
    {
        String value = env.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + label + ". " + "Set it in your .env file or environment before starting the application."
            );
        }
        return value;
    }
}