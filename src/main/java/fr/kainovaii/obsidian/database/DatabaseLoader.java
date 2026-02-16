package fr.kainovaii.obsidian.database;

import fr.kainovaii.obsidian.core.EnvLoader;
import fr.kainovaii.obsidian.core.Obsidian;
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
     * Reads DB_TYPE, DB_PATH/DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD from environment.
     *
     * Default values:
     * - DB_TYPE: sqlite
     * - DB_PATH: data.db (for SQLite)
     * - DB_HOST: localhost
     * - DB_PORT: 3306 (MySQL) or 5432 (PostgreSQL)
     *
     * @throws IllegalArgumentException if database type is not supported
     */
    public static void loadDatabase()
    {
        System.out.println("Loading database");
        EnvLoader env = Obsidian.loadConfigAndEnv();

        String dbType = env.get("DB_TYPE");
        if (dbType == null || dbType.isEmpty()) { dbType = "sqlite"; }

        switch (dbType.toLowerCase())
        {
            case "sqlite":
                String dbPath = env.get("DB_PATH");
                if (dbPath == null || dbPath.isEmpty()) {
                    dbPath = "data.db";
                }
                DB.initSQLite(dbPath, logger);
                break;
            case "mysql":
                String mysqlHost = env.get("DB_HOST");
                String mysqlPort = env.get("DB_PORT");
                DB.initMySQL(
                        mysqlHost != null ? mysqlHost : "localhost",
                        Integer.parseInt(mysqlPort != null ? mysqlPort : "3306"),
                        env.get("DB_NAME"),
                        env.get("DB_USER"),
                        env.get("DB_PASSWORD"),
                        logger
                );
                break;
            case "postgresql":
                String pgHost = env.get("DB_HOST");
                String pgPort = env.get("DB_PORT");
                DB.initPostgreSQL(
                        pgHost != null ? pgHost : "localhost",
                        Integer.parseInt(pgPort != null ? pgPort : "5432"),
                        env.get("DB_NAME"),
                        env.get("DB_USER"),
                        env.get("DB_PASSWORD"),
                        logger
                );
                break;
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }
}
