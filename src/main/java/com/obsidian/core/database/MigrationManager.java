package com.obsidian.core.database;

import com.obsidian.core.core.Obsidian;
import org.javalite.activejdbc.Base;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Migration manager for database schema versioning.
 * Discovers, executes and tracks migrations.
 */
public class MigrationManager
{
    /** Database instance */
    private final DB database;

    /** Logger instance */
    private final Logger logger;

    /** List of registered migrations */
    private final List<Migration> migrations;

    /** Database type */
    private final DatabaseType dbType;

    /**
     * Constructor.
     *
     * @param database Database instance
     * @param logger Logger instance
     */
    public MigrationManager(DB database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.migrations = new ArrayList<>();
        this.dbType = database.getType();
    }

    /**
     * Adds a migration to the list.
     *
     * @param migration Migration instance
     * @return Current instance for chaining
     */
    public MigrationManager add(Migration migration) {
        migration.type = this.dbType;
        migration.logger = this.logger;
        migrations.add(migration);
        return this;
    }

    /**
     * Auto-discovers migrations by scanning base package.
     * Finds all Migration subclasses and instantiates them.
     *
     * @return Current instance for chaining
     */
    public MigrationManager discover()
    {
        try {
            String basePackage = Obsidian.getBasePackage();
            Reflections reflections = new Reflections(basePackage, Scanners.SubTypes);
            Set<Class<? extends Migration>> migrationClasses = reflections.getSubTypesOf(Migration.class);

            List<Migration> discoveredMigrations = new ArrayList<>();

            for (Class<? extends Migration> migrationClass : migrationClasses) {
                try {
                    Migration migration = migrationClass.getDeclaredConstructor().newInstance();
                    migration.type = this.dbType;
                    migration.logger = this.logger;
                    discoveredMigrations.add(migration);
                } catch (Exception e) {
                    logger.warn("Unable to instantiate the migration: " + migrationClass.getName() + " - " + e.getMessage());
                }
            }

            discoveredMigrations.sort(Comparator.comparing(m -> m.getClass().getSimpleName()));
            migrations.addAll(discoveredMigrations);

            logger.info(discoveredMigrations.size() + " migration(s) discovered in " + basePackage);

        } catch (Exception e) {
            logger.error("Error discovering migrations: " + e.getMessage());
        }

        return this;
    }

    /**
     * Executes all pending migrations.
     * Loads executed migrations in a single query, then iterates locally.
     * Runs within a transaction.
     */
    public void migrate() {
        database.executeWithTransaction(() -> {
            createMigrationsTable();
            Set<String> executed = loadExecutedMigrations();

            for (int i = 0; i < migrations.size(); i++) {
                String migrationName = "migration_" + (i + 1);

                if (!executed.contains(migrationName)) {
                    logger.info("Executing migration: " + migrationName);
                    migrations.get(i).up();
                    recordMigration(migrationName);
                    logger.info("✓ Migration completed: " + migrationName);
                } else {
                    logger.info("Migration already executed: " + migrationName);
                }
            }

            logger.info("All migrations are up to date");
            return null;
        });
    }

    /**
     * Rolls back all migrations in reverse order.
     */
    public void rollback() {
        database.executeWithTransaction(() -> {
            Set<String> executed = loadExecutedMigrations();

            for (int i = migrations.size() - 1; i >= 0; i--) {
                String migrationName = "migration_" + (i + 1);

                if (executed.contains(migrationName)) {
                    logger.info("Rolling back migration: " + migrationName);
                    migrations.get(i).down();
                    removeMigration(migrationName);
                    logger.info("✓ Migration rolled back: " + migrationName);
                }
            }

            logger.info("All migrations have been rolled back");
            return null;
        });
    }

    /**
     * Rolls back only the last executed migration.
     */
    public void rollbackLast() {
        database.executeWithTransaction(() -> {
            Set<String> executed = loadExecutedMigrations();

            for (int i = migrations.size() - 1; i >= 0; i--) {
                String migrationName = "migration_" + (i + 1);

                if (executed.contains(migrationName)) {
                    logger.info("Rolling back last migration: " + migrationName);
                    migrations.get(i).down();
                    removeMigration(migrationName);
                    logger.info("✓ Last migration rolled back");
                    break;
                }
            }
            return null;
        });
    }

    /**
     * Rolls back all migrations then re-runs them.
     * Useful for database reset.
     */
    public void fresh() {
        rollback();
        migrate();
    }

    /**
     * Displays migration status (executed vs pending).
     */
    public void status() {
        database.executeWithConnection(() -> {
            Set<String> executed = loadExecutedMigrations();

            for (int i = 0; i < migrations.size(); i++) {
                String migrationName = "migration_" + (i + 1);
                String status = executed.contains(migrationName) ? "✓ Executed" : "✗ Pending";
                logger.info("{} - {}", migrationName, status);
            }
            return null;
        });
    }

    /**
     * Creates migrations tracking table if not exists.
     */
    private void createMigrationsTable() {
        String idColumn = switch (dbType) {
            case MYSQL -> "INT AUTO_INCREMENT PRIMARY KEY";
            case POSTGRESQL -> "SERIAL PRIMARY KEY";
            default -> "INTEGER PRIMARY KEY AUTOINCREMENT";
        };

        Base.exec(String.format("""
            CREATE TABLE IF NOT EXISTS migrations (
                id %s,
                migration VARCHAR(255) NOT NULL,
                executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, idColumn));
    }

    /**
     * Loads all executed migration names in a single query.
     *
     * @return Set of executed migration names
     */
    private Set<String> loadExecutedMigrations() {
        Set<String> executed = new HashSet<>();
        Base.findAll("SELECT migration FROM migrations").forEach(row ->
                executed.add(row.get("migration").toString())
        );
        return executed;
    }

    /**
     * Records a migration as executed.
     *
     * @param migrationName Migration name
     */
    private void recordMigration(String migrationName) {
        Base.exec("INSERT INTO migrations (migration) VALUES (?)", migrationName);
    }

    /**
     * Removes a migration record.
     *
     * @param migrationName Migration name
     */
    private void removeMigration(String migrationName) {
        Base.exec("DELETE FROM migrations WHERE migration = ?", migrationName);
    }
}