package com.obsidian.core.database;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.di.ReflectionsProvider;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Migration manager for database schema versioning.
 * Discovers, executes and tracks migrations.
 * Uses DB static methods instead of ActiveJDBC Base.
 */
public class MigrationManager
{
    private final DB database;
    private final Logger logger;
    private final List<Migration> migrations;
    private final DatabaseType dbType;

    /**
     * Creates a new MigrationManager instance.
     *
     * @param database The database
     * @param logger The logger
     */
    public MigrationManager(DB database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.migrations = new ArrayList<>();
        this.dbType = database.getType();
    }

    /**
     * Add.
     *
     * @param migration The migration
     * @return This instance for method chaining
     */
    public MigrationManager add(Migration migration) {
        migration.type = this.dbType;
        migration.logger = this.logger;
        migrations.add(migration);
        return this;
    }

    /**
     * Discover.
     *
     * @return This instance for method chaining
     */
    public MigrationManager discover()
    {
        try {
            String basePackage = Obsidian.getBasePackage();
            Set<Class<? extends Migration>> migrationClasses = ReflectionsProvider.getSubTypesOf(Migration.class);

            List<Migration> discoveredMigrations = new ArrayList<>();

            for (Class<? extends Migration> migrationClass : migrationClasses) {
                // Restrict to the application's own package — prevents third-party
                // dependencies that happen to extend Migration from being executed.
                if (!migrationClass.getName().startsWith(basePackage)) {
                    logger.debug("Skipping migration outside base package: {}", migrationClass.getName());
                    continue;
                }
                try {
                    Migration migration = migrationClass.getDeclaredConstructor().newInstance();
                    migration.type = this.dbType;
                    migration.logger = this.logger;
                    discoveredMigrations.add(migration);
                } catch (Exception e) {
                    logger.warn("Unable to instantiate migration {}: {}", migrationClass.getName(), e.getMessage());
                }
            }

            discoveredMigrations.sort(Comparator.comparing(m -> m.getClass().getSimpleName()));
            migrations.addAll(discoveredMigrations);

            logger.info("{} migration(s) discovered in {}", discoveredMigrations.size(), basePackage);

        } catch (Exception e) {
            logger.error("Error discovering migrations: {}", e.getMessage());
        }

        return this;
    }

    /**
     * Migrate.
     *
     */
    public void migrate() {
        database.executeWithTransaction(() -> {
            createMigrationsTable();
            Set<String> executed = loadExecutedMigrations();

            for (Migration migration : migrations) {
                String migrationName = migration.getClass().getSimpleName();

                if (!executed.contains(migrationName)) {
                    logger.info("Executing migration: {}", migrationName);
                    migration.up();
                    recordMigration(migrationName);
                    logger.info("Migration completed: {}", migrationName);
                } else {
                    logger.info("Migration already executed: {}", migrationName);
                }
            }

            logger.info("All migrations are up to date");
            return null;
        });
    }

    /**
     * Rollback.
     *
     */
    public void rollback() {
        database.executeWithTransaction(() -> {
            Set<String> executed = loadExecutedMigrations();

            for (int i = migrations.size() - 1; i >= 0; i--) {
                Migration migration = migrations.get(i);
                String migrationName = migration.getClass().getSimpleName();

                if (executed.contains(migrationName)) {
                    logger.info("Rolling back migration: {}", migrationName);
                    migration.down();
                    removeMigration(migrationName);
                    logger.info("Migration rolled back: {}", migrationName);
                }
            }

            logger.info("All migrations have been rolled back");
            return null;
        });
    }

    /**
     * Rollback Last.
     *
     */
    public void rollbackLast() {
        database.executeWithTransaction(() -> {
            Set<String> executed = loadExecutedMigrations();

            for (int i = migrations.size() - 1; i >= 0; i--) {
                Migration migration = migrations.get(i);
                String migrationName = migration.getClass().getSimpleName();

                if (executed.contains(migrationName)) {
                    logger.info("Rolling back last migration: {}", migrationName);
                    migration.down();
                    removeMigration(migrationName);
                    logger.info("Last migration rolled back: {}", migrationName);
                    break;
                }
            }
            return null;
        });
    }

    /**
     * Fresh.
     *
     */
    public void fresh() {
        rollback();
        migrate();
    }

    /**
     * Status.
     *
     */
    public void status() {
        database.executeWithConnection(() -> {
            Set<String> executed = loadExecutedMigrations();

            for (Migration migration : migrations) {
                String migrationName = migration.getClass().getSimpleName();
                String status = executed.contains(migrationName) ? "Executed" : "Pending";
                logger.info("{} — {}", migrationName, status);
            }
            return null;
        });
    }

    // ─── PRIVATE HELPERS (using DB instead of Base) ──────────

    private void createMigrationsTable() {
        String idColumn = switch (dbType) {
            case MYSQL -> "INT AUTO_INCREMENT PRIMARY KEY";
            case POSTGRESQL -> "SERIAL PRIMARY KEY";
            default -> "INTEGER PRIMARY KEY AUTOINCREMENT";
        };

        DB.exec(String.format("""
            CREATE TABLE IF NOT EXISTS migrations (
                id %s,
                migration VARCHAR(255) NOT NULL,
                executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """, idColumn));
    }

    private Set<String> loadExecutedMigrations() {
        Set<String> executed = new HashSet<>();
        DB.findAll("SELECT migration FROM migrations").forEach(row ->
                executed.add(row.get("migration").toString())
        );
        return executed;
    }

    private void recordMigration(String migrationName) {
        DB.exec("INSERT INTO migrations (migration) VALUES (?)", migrationName);
    }

    private void removeMigration(String migrationName) {
        DB.exec("DELETE FROM migrations WHERE migration = ?", migrationName);
    }
}