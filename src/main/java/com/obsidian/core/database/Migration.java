package com.obsidian.core.database;

import com.obsidian.core.database.orm.query.SqlIdentifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for database migrations.
 *
 * @see Blueprint
 */
public abstract class Migration
{
    /** Database type — set by MigrationManager before calling up() / down(). */
    protected DatabaseType type;

    /** Logger — set by MigrationManager before calling up() / down(). */
    protected Logger logger;

    /**
     * Applies the migration.
     */
    public abstract void up();

    /**
     * Reverts the migration.
     */
    public abstract void down();

    /**
     * Creates a table using a Blueprint callback.
     *
     * @param tableName table name, must be a valid SQL identifier
     * @param builder   callback that receives a {@link Blueprint} to define columns
     */
    protected void createTable(String tableName, TableBuilder builder)
    {
        SqlIdentifier.requireIdentifier(tableName);

        List<String> columns     = new ArrayList<>();
        List<String> constraints = new ArrayList<>();
        builder.build(new Blueprint(columns, constraints, type));

        List<String> allParts = new ArrayList<>(columns);
        allParts.addAll(constraints);

        DB.exec("CREATE TABLE IF NOT EXISTS " + tableName +
                " (" + String.join(", ", allParts) + ")");
        logger.info("Table created: {}", tableName);
    }

    /**
     * Drops a table if it exists.
     *
     * @param tableName table name, must be a valid SQL identifier
     */
    protected void dropTable(String tableName)
    {
        SqlIdentifier.requireIdentifier(tableName);
        DB.exec("DROP TABLE IF EXISTS " + tableName);
        logger.info("Table dropped: {}", tableName);
    }

    /**
     * Adds a column to an existing table.
     *
     * @param tableName  table name
     * @param columnName column name
     * @param definition raw column type definition, e.g. {@code "VARCHAR(255) NOT NULL"}
     */
    protected void addColumn(String tableName, String columnName, String definition)
    {
        SqlIdentifier.requireIdentifier(tableName);
        SqlIdentifier.requireIdentifier(columnName);
        DB.exec("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        logger.info("Column added: {}.{}", tableName, columnName);
    }

    /**
     * Drops a column from a table. No-op on SQLite, which does not support DROP COLUMN.
     *
     * @param tableName  table name
     * @param columnName column name
     */
    protected void dropColumn(String tableName, String columnName)
    {
        if (type == DatabaseType.SQLITE) {
            logger.warn("SQLite does not support DROP COLUMN — skipped for column: {}", columnName);
            return;
        }
        SqlIdentifier.requireIdentifier(tableName);
        SqlIdentifier.requireIdentifier(columnName);
        DB.exec("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        logger.info("Column dropped: {}.{}", tableName, columnName);
    }

    /**
     * Renames a table.
     *
     * @param from current table name
     * @param to   new table name
     */
    protected void renameTable(String from, String to)
    {
        SqlIdentifier.requireIdentifier(from);
        SqlIdentifier.requireIdentifier(to);
        DB.exec("ALTER TABLE " + from + " RENAME TO " + to);
        logger.info("Table renamed: {} -> {}", from, to);
    }

    /**
     * Returns {@code true} if the given table exists in the database.
     *
     * @param tableName table name to check
     * @return {@code true} if the table exists
     */
    protected boolean tableExists(String tableName)
    {
        String sql = switch (type) {
            case MYSQL      -> "SELECT COUNT(*) FROM information_schema.tables " + "WHERE table_schema = DATABASE() AND table_name = ?";
            case POSTGRESQL -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?";
            default         -> "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
        };
        Object result = DB.firstCell(sql, tableName);
        if (result == null) return false;
        long count = result instanceof Long l ? l : Long.parseLong(result.toString());
        return count > 0;
    }

    /**
     * Executes a raw SQL statement with bound parameters.
     * The caller is responsible for ensuring {@code sql} is safe.
     *
     * @param sql    raw SQL string
     * @param params values bound via PreparedStatement
     */
    protected void raw(String sql, Object... params) {
        DB.exec(sql, params);
    }

    /**
     * Callback used by {@link #createTable} to define columns on a {@link Blueprint}.
     */
    @FunctionalInterface
    public interface TableBuilder
    {
        /**
         * Defines columns on the given blueprint.
         *
         * @param blueprint blueprint to configure
         */
        void build(Blueprint blueprint);
    }
}