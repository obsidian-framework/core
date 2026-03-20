package com.obsidian.core.database;

import com.obsidian.core.database.orm.query.SqlIdentifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for database migrations.
 * Provides schema modification methods with multi-database support.
 * Uses DB.exec() instead of ActiveJDBC Base.exec().
 *
 * <p><b>Security</b>: every table name and column name passed to DDL methods
 * is validated by {@link SqlIdentifier#requireIdentifier(String)} before being
 * interpolated into SQL. DDL statements cannot use PreparedStatement placeholders
 * for identifiers, so this guard is the only defence against injection through
 * dynamically-constructed migration names.</p>
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
     * @param tableName the table name — must be a valid SQL identifier
     * @param builder   the column/constraint builder callback
     */
    protected void createTable(String tableName, TableBuilder builder)
    {
        SqlIdentifier.requireIdentifier(tableName);

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");

        List<String> columns = new ArrayList<>();
        List<String> constraints = new ArrayList<>();
        builder.build(new Blueprint(columns, constraints, type));

        List<String> allParts = new ArrayList<>(columns);
        allParts.addAll(constraints);

        sql.append(String.join(", ", allParts));
        sql.append(")");

        DB.exec(sql.toString());
        logger.info("Table created: {}", tableName);
    }

    /**
     * Drops a table if it exists.
     *
     * @param tableName the table name — must be a valid SQL identifier
     */
    protected void dropTable(String tableName) {
        SqlIdentifier.requireIdentifier(tableName);
        DB.exec("DROP TABLE IF EXISTS " + tableName);
        logger.info("Table dropped: {}", tableName);
    }

    /**
     * Adds a column to an existing table.
     *
     * @param tableName  the table name — must be a valid SQL identifier
     * @param columnName the column name — must be a valid SQL identifier
     * @param definition the raw column type definition (e.g. "VARCHAR(255) NOT NULL")
     */
    protected void addColumn(String tableName, String columnName, String definition) {
        SqlIdentifier.requireIdentifier(tableName);
        SqlIdentifier.requireIdentifier(columnName);
        DB.exec("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        logger.info("Column added: {}.{}", tableName, columnName);
    }

    /**
     * Drops a column from a table.
     * No-op on SQLite (not supported).
     *
     * @param tableName  the table name — must be a valid SQL identifier
     * @param columnName the column name — must be a valid SQL identifier
     */
    protected void dropColumn(String tableName, String columnName) {
        if (type == DatabaseType.SQLITE) {
            logger.warn("SQLite does not support DROP COLUMN — migration skipped for column: {}", columnName);
            return;
        }
        SqlIdentifier.requireIdentifier(tableName);
        SqlIdentifier.requireIdentifier(columnName);
        DB.exec("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        logger.info("Column dropped: {}.{}", tableName, columnName);
    }

    /**
     * Checks if a table exists in the database.
     *
     * @param tableName the table name to check (used as a bound parameter, not interpolated)
     * @return {@code true} if the table exists
     */
    protected boolean tableExists(String tableName)
    {
        String checkSQL = switch (type) {
            case MYSQL      -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            case POSTGRESQL -> "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?";
            default         -> "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?";
        };

        Object result = DB.firstCell(checkSQL, tableName);
        if (result == null) return false;

        long count = result instanceof Long ? (Long) result : Long.parseLong(result.toString());
        return count > 0;
    }

    /**
     * Executes raw SQL with bound parameters.
     * The caller is responsible for ensuring {@code sql} is safe.
     *
     * @param sql    raw SQL (trusted, not derived from user input)
     * @param params values bound via PreparedStatement
     */
    protected void raw(String sql, Object... params) {
        DB.exec(sql, params);
    }

    /**
     * Renames a table.
     *
     * @param from the current table name — must be a valid SQL identifier
     * @param to   the new table name — must be a valid SQL identifier
     */
    protected void renameTable(String from, String to) {
        SqlIdentifier.requireIdentifier(from);
        SqlIdentifier.requireIdentifier(to);
        DB.exec("ALTER TABLE " + from + " RENAME TO " + to);
        logger.info("Table renamed: {} -> {}", from, to);
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
     *
     * Enhanced from original with:
     * - foreignKey() support
     * - cascadeOnDelete() / nullOnDelete()
     * - softDeletes()
     * - json() column type
     * - Separate constraints list for foreign keys / composite indexes
     */
    public static class Blueprint {
        private final List<String> columns;
        private final List<String> constraints;
        private final DatabaseType dbType;

        /**
         * Creates a new Blueprint instance.
         *
         * @param columns     column definitions list (mutated by builder methods)
         * @param constraints constraint definitions list (mutated by builder methods)
         * @param dbType      the target database type
         */
        public Blueprint(List<String> columns, List<String> constraints, DatabaseType dbType) {
            this.columns = columns;
            this.constraints = constraints;
            this.dbType = dbType;
        }

        /**
         * Backward-compatible 2-arg constructor.
         *
         * @param columns column definitions list
         * @param dbType  the target database type
         */
        public Blueprint(List<String> columns, DatabaseType dbType) {
            this(columns, new ArrayList<>(), dbType);
        }

        // ─── INTERNAL COLUMN HELPERS ─────────────────────────

        /**
         * Central column-addition helper — validates the column name via
         * {@link SqlIdentifier#requireIdentifier} before interpolating it
         * into DDL. All public Blueprint methods that accept a column name
         * must route through here instead of calling {@code columns.add}
         * directly, preventing DDL injection via malformed column names.
         *
         * <p>{@code type} is always a hard-coded literal produced by this
         * class (e.g. {@code "TEXT"}, {@code "VARCHAR(255)"}), never derived
         * from caller input, so it does not need separate validation.</p>
         */
        private Blueprint col(String name, String type) {
            SqlIdentifier.requireIdentifier(name);
            columns.add(name + " " + type);
            return this;
        }

        /**
         * Validates and records a FOREIGN KEY constraint for the last column.
         * Both the referenced table and column are validated as identifiers.
         */
        private Blueprint fk(String refTable, String refColumn, String colName) {
            SqlIdentifier.requireIdentifier(refTable);
            SqlIdentifier.requireIdentifier(refColumn);
            constraints.add("FOREIGN KEY (" + colName + ") REFERENCES " + refTable + "(" + refColumn + ")");
            return this;
        }

        // ─── PRIMARY KEY ─────────────────────────────────────

        /**
         * Adds an auto-increment primary key column named {@code id}.
         *
         * @return This instance for method chaining
         */
        public Blueprint id() {
            return id("id");
        }

        /**
         * Adds an auto-increment primary key column with a custom name.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint id(String name) {
            SqlIdentifier.requireIdentifier(name);
            String type = switch (dbType) {
                case MYSQL      -> "INT AUTO_INCREMENT PRIMARY KEY";
                case POSTGRESQL -> "SERIAL PRIMARY KEY";
                default         -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            };
            columns.add(name + " " + type);
            return this;
        }

        /**
         * UUID primary key.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint uuid(String name) {
            return col(name, "VARCHAR(36)");
        }

        // ─── STRING / TEXT ───────────────────────────────────

        /**
         * String column (VARCHAR 255 or TEXT on SQLite).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint string(String name) {
            return string(name, 255);
        }

        /**
         * String column with a custom length.
         *
         * @param name   the column name
         * @param length the maximum length
         * @return This instance for method chaining
         */
        public Blueprint string(String name, int length) {
            String type = dbType == DatabaseType.SQLITE ? "TEXT" : "VARCHAR(" + length + ")";
            return col(name, type);
        }

        /**
         * TEXT column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint text(String name) {
            return col(name, "TEXT");
        }

        /**
         * MEDIUMTEXT column (TEXT on non-MySQL).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint mediumText(String name) {
            String type = dbType == DatabaseType.MYSQL ? "MEDIUMTEXT" : "TEXT";
            return col(name, type);
        }

        /**
         * LONGTEXT column (TEXT on non-MySQL).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint longText(String name) {
            String type = dbType == DatabaseType.MYSQL ? "LONGTEXT" : "TEXT";
            return col(name, type);
        }

        // ─── NUMERIC ─────────────────────────────────────────

        /**
         * INT / INTEGER column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint integer(String name) {
            String type = dbType == DatabaseType.POSTGRESQL ? "INTEGER" : "INT";
            return col(name, type);
        }

        /**
         * TINYINT column (INTEGER on SQLite).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint tinyInteger(String name) {
            String type = dbType == DatabaseType.SQLITE ? "INTEGER" : "TINYINT";
            return col(name, type);
        }

        /**
         * SMALLINT column (INTEGER on SQLite).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint smallInteger(String name) {
            String type = dbType == DatabaseType.SQLITE ? "INTEGER" : "SMALLINT";
            return col(name, type);
        }

        /**
         * BIGINT column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint bigInteger(String name) {
            return col(name, "BIGINT");
        }

        /**
         * DECIMAL column with explicit precision and scale.
         *
         * @param name      the column name
         * @param precision total digits
         * @param scale     digits after decimal point
         * @return This instance for method chaining
         */
        public Blueprint decimal(String name, int precision, int scale) {
            return col(name, "DECIMAL(" + precision + "," + scale + ")");
        }

        /**
         * DECIMAL(10,2) column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint decimal(String name) {
            return decimal(name, 10, 2);
        }

        /**
         * FLOAT column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint floatCol(String name) {
            return col(name, "FLOAT");
        }

        /**
         * DOUBLE column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint doubleCol(String name) {
            return col(name, "DOUBLE");
        }

        // ─── BOOLEAN ─────────────────────────────────────────

        /**
         * BOOLEAN column (INTEGER on SQLite).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint bool(String name) {
            String type = dbType == DatabaseType.SQLITE ? "INTEGER" : "BOOLEAN";
            return col(name, type);
        }

        // ─── DATE / TIME ─────────────────────────────────────

        /**
         * DATE column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint date(String name) {
            return col(name, "DATE");
        }

        /**
         * DATETIME / TIMESTAMP / TEXT column depending on database.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint dateTime(String name) {
            String type = switch (dbType) {
                case POSTGRESQL -> "TIMESTAMP";
                case MYSQL      -> "DATETIME";
                default         -> "TEXT";
            };
            return col(name, type);
        }

        /**
         * TIMESTAMP column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint timestamp(String name) {
            return col(name, "TIMESTAMP");
        }

        /**
         * TIME column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint time(String name) {
            return col(name, "TIME");
        }

        /**
         * Adds {@code created_at} and {@code updated_at} columns with appropriate defaults.
         *
         * @return This instance for method chaining
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
         * Adds a nullable {@code deleted_at} column for soft deletes.
         *
         * @return This instance for method chaining
         */
        public Blueprint softDeletes() {
            dateTime("deleted_at");
            nullable();
            return this;
        }

        // ─── JSON ────────────────────────────────────────────

        /**
         * JSON column (TEXT on SQLite).
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint json(String name) {
            String type = dbType == DatabaseType.SQLITE ? "TEXT" : "JSON";
            return col(name, type);
        }

        // ─── BLOB ────────────────────────────────────────────

        /**
         * BLOB column.
         *
         * @param name the column name
         * @return This instance for method chaining
         */
        public Blueprint blob(String name) {
            return col(name, "BLOB");
        }

        // ─── ENUM ────────────────────────────────────────────

        /**
         * ENUM column (CHECK constraint on PostgreSQL / SQLite).
         *
         * @param name   the column name
         * @param values allowed enum values
         * @return This instance for method chaining
         */
        public Blueprint enumCol(String name, String... values) {
            if (dbType == DatabaseType.MYSQL) {
                String vals = "'" + String.join("', '", values) + "'";
                col(name, "ENUM(" + vals + ")");
            } else {
                col(name, "VARCHAR(50)");
                String vals = "'" + String.join("', '", values) + "'";
                constraints.add("CHECK (" + name + " IN (" + vals + "))");
            }
            return this;
        }

        // ─── MODIFIERS ───────────────────────────────────────

        /**
         * Appends NOT NULL to the last column definition.
         *
         * @return This instance for method chaining
         */
        public Blueprint notNull() {
            modifyLast(" NOT NULL");
            return this;
        }

        /**
         * Appends UNIQUE to the last column definition.
         *
         * @return This instance for method chaining
         */
        public Blueprint unique() {
            modifyLast(" UNIQUE");
            return this;
        }

        /**
         * Appends a DEFAULT clause to the last column definition.
         *
         * @param value the default value string (not quoted)
         * @return This instance for method chaining
         */
        public Blueprint defaultValue(String value) {
            modifyLast(" DEFAULT " + value);
            return this;
        }

        /**
         * Appends a DEFAULT clause with an integer value.
         *
         * @param value the default integer value
         * @return This instance for method chaining
         */
        public Blueprint defaultValue(int value) {
            modifyLast(" DEFAULT " + value);
            return this;
        }

        /**
         * Appends a DEFAULT clause with a boolean value (1 or 0).
         *
         * @param value the default boolean value
         * @return This instance for method chaining
         */
        public Blueprint defaultValue(boolean value) {
            modifyLast(" DEFAULT " + (value ? "1" : "0"));
            return this;
        }

        /**
         * No-op: columns are nullable by default. Provided for readability.
         *
         * @return This instance for method chaining
         */
        public Blueprint nullable() {
            return this;
        }

        /**
         * Adds a FOREIGN KEY constraint referencing the last column.
         *
         * @param refTable  the referenced table name
         * @param refColumn the referenced column name
         * @return This instance for method chaining
         */
        public Blueprint foreignKey(String refTable, String refColumn) {
            if (!columns.isEmpty()) {
                String lastCol = columns.get(columns.size() - 1);
                String colName = lastCol.split("\\s+")[0];
                // colName is already validated (extracted from a col() call above)
                // refTable and refColumn are validated inside fk()
                fk(refTable, refColumn, colName);
            }
            return this;
        }

        /**
         * Appends ON DELETE CASCADE to the last foreign key constraint.
         *
         * @return This instance for method chaining
         */
        public Blueprint cascadeOnDelete() {
            modifyLastConstraint(" ON DELETE CASCADE");
            return this;
        }

        /**
         * Appends ON DELETE SET NULL to the last foreign key constraint.
         *
         * @return This instance for method chaining
         */
        public Blueprint nullOnDelete() {
            modifyLastConstraint(" ON DELETE SET NULL");
            return this;
        }

        /**
         * Appends ON DELETE RESTRICT to the last foreign key constraint.
         *
         * @return This instance for method chaining
         */
        public Blueprint restrictOnDelete() {
            modifyLastConstraint(" ON DELETE RESTRICT");
            return this;
        }

        /**
         * Appends ON UPDATE CASCADE to the last foreign key constraint.
         *
         * @return This instance for method chaining
         */
        public Blueprint cascadeOnUpdate() {
            modifyLastConstraint(" ON UPDATE CASCADE");
            return this;
        }

        // ─── INDEX SHORTCUTS ─────────────────────────────────

        /**
         * Adds a composite UNIQUE constraint.
         *
         * @param columnNames the columns forming the unique index
         * @return This instance for method chaining
         */
        public Blueprint uniqueIndex(String... columnNames) {
            for (String col : columnNames) {
                SqlIdentifier.requireIdentifier(col);
            }
            constraints.add("UNIQUE (" + String.join(", ", columnNames) + ")");
            return this;
        }

        /**
         * Adds {@code name_id} (BIGINT NOT NULL) and {@code name_type} (VARCHAR NOT NULL) columns
         * for polymorphic relations.
         *
         * @param name the morph base name
         * @return This instance for method chaining
         */
        public Blueprint morphs(String name) {
            bigInteger(name + "_id").notNull();
            string(name + "_type").notNull();
            return this;
        }

        // ─── INTERNAL HELPERS ────────────────────────────────

        private void modifyLast(String suffix) {
            if (!columns.isEmpty()) {
                int lastIndex = columns.size() - 1;
                columns.set(lastIndex, columns.get(lastIndex) + suffix);
            }
        }

        private void modifyLastConstraint(String suffix) {
            if (!constraints.isEmpty()) {
                int lastIndex = constraints.size() - 1;
                constraints.set(lastIndex, constraints.get(lastIndex) + suffix);
            }
        }
    }
}