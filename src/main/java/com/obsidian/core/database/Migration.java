package com.obsidian.core.database;

import org.javalite.activejdbc.Base;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for database migrations.
 * Provides schema modification methods with multi-database support.
 */
public abstract class Migration
{
    /** Database type */
    protected DatabaseType type;

    /** Logger instance */
    protected Logger logger;

    /**
     * Executes migration (creates/modifies schema).
     */
    public abstract void up();

    /**
     * Reverts migration (rolls back changes).
     */
    public abstract void down();

    /**
     * Creates a new table with specified columns.
     *
     * @param tableName Name of table to create
     * @param builder Callback to define table structure
     */
    protected void createTable(String tableName, TableBuilder builder)
    {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");

        List<String> columns = new ArrayList<>();
        builder.build(new Blueprint(columns, type));

        sql.append(String.join(", ", columns));
        sql.append(")");

        Base.exec(sql.toString());
        logger.info("Table created: " + tableName);
    }

    /**
     * Drops a table if it exists.
     *
     * @param tableName Name of table to drop
     */
    protected void dropTable(String tableName) {
        Base.exec("DROP TABLE IF EXISTS " + tableName);
        logger.info("Table dropped: " + tableName);
    }

    /**
     * Adds a column to existing table.
     *
     * @param tableName Table name
     * @param columnName Column name
     * @param definition Column definition (type and constraints)
     */
    protected void addColumn(String tableName, String columnName, String definition) {
        Base.exec(String.format("ALTER TABLE %s ADD COLUMN %s %s", tableName, columnName, definition));
        logger.info("Column added: " + tableName + "." + columnName);
    }

    /**
     * Drops a column from table.
     * Note: Not supported in SQLite.
     *
     * @param tableName Table name
     * @param columnName Column name
     */
    protected void dropColumn(String tableName, String columnName) {
        if (type == DatabaseType.SQLITE) {
            logger.warn("SQLite does not support DROP COLUMN - migration skipped");
            return;
        }
        Base.exec(String.format("ALTER TABLE %s DROP COLUMN %s", tableName, columnName));
        logger.info("Column dropped: " + tableName + "." + columnName);
    }

    /**
     * Checks if a table exists in database.
     *
     * @param tableName Table name to check
     * @return true if table exists, false otherwise
     */
    protected boolean tableExists(String tableName)
    {
        String checkSQL = switch (type) {
            case MYSQL -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            case POSTGRESQL -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?";
            default -> "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
        };

        Object result = Base.firstCell(checkSQL, tableName);
        if (result == null) return false;

        long count = result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        return count > 0;
    }

    /**
     * Functional interface for table building.
     */
    @FunctionalInterface
    public interface TableBuilder {
        void build(Blueprint blueprint);
    }

    /**
     * Schema builder for defining table columns.
     * Provides fluent API for column definitions with database-specific syntax.
     */
    public static class Blueprint {
        private final List<String> columns;
        private final DatabaseType dbType;

        public Blueprint(List<String> columns, DatabaseType dbType) {
            this.columns = columns;
            this.dbType = dbType;
        }

        /**
         * Adds auto-incrementing primary key named "id".
         */
        public Blueprint id() {
            return id("id");
        }

        /**
         * Adds auto-incrementing primary key with custom name.
         *
         * @param name Column name
         */
        public Blueprint id(String name) {
            String column = switch (dbType) {
                case MYSQL -> name + " INT AUTO_INCREMENT PRIMARY KEY";
                case POSTGRESQL -> name + " SERIAL PRIMARY KEY";
                default -> name + " INTEGER PRIMARY KEY AUTOINCREMENT";
            };
            columns.add(column);
            return this;
        }

        /**
         * Adds VARCHAR/TEXT column with default length 255.
         *
         * @param name Column name
         */
        public Blueprint string(String name) {
            return string(name, 255);
        }

        /**
         * Adds VARCHAR/TEXT column with custom length.
         *
         * @param name Column name
         * @param length Maximum length
         */
        public Blueprint string(String name, int length) {
            String type = dbType == DatabaseType.SQLITE ? "TEXT" : "VARCHAR(" + length + ")";
            columns.add(name + " " + type);
            return this;
        }

        /**
         * Adds TEXT column.
         *
         * @param name Column name
         */
        public Blueprint text(String name) {
            columns.add(name + " TEXT");
            return this;
        }

        /**
         * Adds INTEGER column.
         *
         * @param name Column name
         */
        public Blueprint integer(String name) {
            String type = dbType == DatabaseType.POSTGRESQL ? "INTEGER" : "INT";
            columns.add(name + " " + type);
            return this;
        }

        /**
         * Adds BIGINT column.
         *
         * @param name Column name
         */
        public Blueprint bigInteger(String name) {
            columns.add(name + " BIGINT");
            return this;
        }

        /**
         * Adds DECIMAL column.
         *
         * @param name Column name
         * @param precision Total digits
         * @param scale Decimal places
         */
        public Blueprint decimal(String name, int precision, int scale) {
            columns.add(name + " DECIMAL(" + precision + "," + scale + ")");
            return this;
        }

        /**
         * Adds BOOLEAN column.
         *
         * @param name Column name
         */
        public Blueprint bool(String name) {
            String type = dbType == DatabaseType.SQLITE ? "INTEGER" : "BOOLEAN";
            columns.add(name + " " + type);
            return this;
        }

        /**
         * Adds DATE column.
         *
         * @param name Column name
         */
        public Blueprint date(String name) {
            columns.add(name + " DATE");
            return this;
        }

        /**
         * Adds DATETIME/TIMESTAMP column.
         *
         * @param name Column name
         */
        public Blueprint dateTime(String name) {
            String type = switch (dbType) {
                case POSTGRESQL -> "TIMESTAMP";
                case MYSQL -> "DATETIME";
                default -> "TEXT";
            };
            columns.add(name + " " + type);
            return this;
        }

        /**
         * Adds TIMESTAMP column.
         *
         * @param name Column name
         */
        public Blueprint timestamp(String name) {
            columns.add(name + " TIMESTAMP");
            return this;
        }

        /**
         * Adds created_at and updated_at timestamp columns.
         */
        public Blueprint timestamps()
        {
            if (dbType == DatabaseType.MYSQL) {
                columns.add("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                columns.add("updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            } else if (dbType == DatabaseType.POSTGRESQL) {
                columns.add("created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                columns.add("updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } else {
                columns.add("created_at TEXT DEFAULT CURRENT_TIMESTAMP");
                columns.add("updated_at TEXT DEFAULT CURRENT_TIMESTAMP");
            }
            return this;
        }

        /**
         * Adds NOT NULL constraint to last column.
         */
        public Blueprint notNull() {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + " NOT NULL");
            }
            return this;
        }

        /**
         * Adds UNIQUE constraint to last column.
         */
        public Blueprint unique() {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + " UNIQUE");
            }
            return this;
        }

        /**
         * Adds DEFAULT value to last column.
         *
         * @param value Default value
         */
        public Blueprint defaultValue(String value) {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + " DEFAULT " + value);
            }
            return this;
        }

        /**
         * Marks last column as nullable (no-op, columns are nullable by default).
         */
        public Blueprint nullable() {
            return this;
        }
    }
}