package com.obsidian.core.database;

import com.obsidian.core.core.EnvKeys;
import com.obsidian.core.core.EnvLoader;
import com.obsidian.core.core.Obsidian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database connection loader.
 * Initializes database connection based on environment configuration.
 * Supports SQLite, MySQL, and PostgreSQL.
 */
public class DatabaseLoader
{
    /** Logger instance */
    public final static Logger logger = LoggerFactory.getLogger(DatabaseLoader.class);

    /**
     * Loads and initializes database connection from environment configuration.
     *
     * @throws IllegalArgumentException if database type is not supported
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
                String mysqlHost = env.get(EnvKeys.DB_HOST);
                String mysqlPort = env.get(EnvKeys.DB_PORT);
                DB.initMySQL(
                        mysqlHost != null ? mysqlHost : "localhost",
                        Integer.parseInt(mysqlPort != null ? mysqlPort : "3306"),
                        env.get(EnvKeys.DB_NAME),
                        env.get(EnvKeys.DB_USER),
                        env.get(EnvKeys.DB_PASSWORD),
                        logger
                );
                break;
            case POSTGRESQL:
                String pgHost = env.get(EnvKeys.DB_HOST);
                String pgPort = env.get(EnvKeys.DB_PORT);
                DB.initPostgreSQL(
                        pgHost != null ? pgHost : "localhost",
                        Integer.parseInt(pgPort != null ? pgPort : "5432"),
                        env.get(EnvKeys.DB_NAME),
                        env.get(EnvKeys.DB_USER),
                        env.get(EnvKeys.DB_PASSWORD),
                        logger
                );
                break;
        }
    }
}