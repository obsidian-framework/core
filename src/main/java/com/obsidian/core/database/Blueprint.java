package com.obsidian.core.database;

import com.obsidian.core.database.orm.query.SqlIdentifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent schema builder for defining table columns and constraints.
 *
 * <p>Used inside {@link Migration#createTable} callbacks. Every column name
 * is validated by {@link SqlIdentifier#requireIdentifier} before being
 * interpolated into DDL.</p>
 */
public class Blueprint {

    private final List<String> columns;
    private final List<String> constraints;
    private final DatabaseType dbType;

    /**
     * Creates a Blueprint with separate column and constraint lists.
     *
     * @param columns     column definitions list (mutated in place)
     * @param constraints constraint definitions list (mutated in place)
     * @param dbType      target database type
     */
    public Blueprint(List<String> columns, List<String> constraints, DatabaseType dbType) {
        this.columns     = columns;
        this.constraints = constraints;
        this.dbType      = dbType;
    }

    /**
     * Creates a Blueprint without a constraint list.
     *
     * @param columns column definitions list
     * @param dbType  target database type
     */
    public Blueprint(List<String> columns, DatabaseType dbType) {
        this(columns, new ArrayList<>(), dbType);
    }

    // ─── INTERNAL ────────────────────────────────────────────

    private Blueprint col(String name, String type) {
        SqlIdentifier.requireIdentifier(name);
        columns.add(name + " " + type);
        return this;
    }

    private Blueprint fk(String refTable, String refColumn, String colName) {
        SqlIdentifier.requireIdentifier(refTable);
        SqlIdentifier.requireIdentifier(refColumn);
        constraints.add("FOREIGN KEY (" + colName + ") REFERENCES " + refTable + "(" + refColumn + ")");
        return this;
    }

    private void modifyLast(String suffix) {
        if (!columns.isEmpty()) {
            int i = columns.size() - 1;
            columns.set(i, columns.get(i) + suffix);
        }
    }

    private void modifyLastConstraint(String suffix) {
        if (!constraints.isEmpty()) {
            int i = constraints.size() - 1;
            constraints.set(i, constraints.get(i) + suffix);
        }
    }

    // ─── PRIMARY KEY ─────────────────────────────────────────

    /** Adds an auto-increment primary key column named id. */
    public Blueprint id() { return id("id"); }

    /**
     * Adds an auto-increment primary key column.
     *
     * @param name column name
     * @return this blueprint
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
     * Adds a UUID column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint uuid(String name) { return col(name, "VARCHAR(36)"); }

    // ─── STRING / TEXT ───────────────────────────────────────

    /**
     * Adds a VARCHAR(255) / TEXT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint string(String name) { return string(name, 255); }

    /**
     * Adds a VARCHAR(length) / TEXT column.
     *
     * @param name   column name
     * @param length max length
     * @return this blueprint
     */
    public Blueprint string(String name, int length) {
        return col(name, dbType == DatabaseType.SQLITE ? "TEXT" : "VARCHAR(" + length + ")");
    }

    /**
     * Adds a TEXT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint text(String name) { return col(name, "TEXT"); }

    /**
     * Adds a MEDIUMTEXT / TEXT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint mediumText(String name) {
        return col(name, dbType == DatabaseType.MYSQL ? "MEDIUMTEXT" : "TEXT");
    }

    /**
     * Adds a LONGTEXT / TEXT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint longText(String name) {
        return col(name, dbType == DatabaseType.MYSQL ? "LONGTEXT" : "TEXT");
    }

    // ─── NUMERIC ─────────────────────────────────────────────

    /**
     * Adds an INT / INTEGER column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint integer(String name) {
        return col(name, dbType == DatabaseType.POSTGRESQL ? "INTEGER" : "INT");
    }

    /**
     * Adds a TINYINT / INTEGER column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint tinyInteger(String name) {
        return col(name, dbType == DatabaseType.SQLITE ? "INTEGER" : "TINYINT");
    }

    /**
     * Adds a SMALLINT / INTEGER column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint smallInteger(String name) {
        return col(name, dbType == DatabaseType.SQLITE ? "INTEGER" : "SMALLINT");
    }

    /**
     * Adds a BIGINT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint bigInteger(String name) { return col(name, "BIGINT"); }

    /**
     * Adds a DECIMAL(precision, scale) column.
     *
     * @param name      column name
     * @param precision total digits
     * @param scale     digits after the decimal point
     * @return this blueprint
     */
    public Blueprint decimal(String name, int precision, int scale) {
        return col(name, "DECIMAL(" + precision + "," + scale + ")");
    }

    /**
     * Adds a DECIMAL(10,2) column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint decimal(String name) { return decimal(name, 10, 2); }

    /**
     * Adds a FLOAT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint floatCol(String name)  { return col(name, "FLOAT"); }

    /**
     * Adds a DOUBLE column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint doubleCol(String name) { return col(name, "DOUBLE"); }

    // ─── BOOLEAN ─────────────────────────────────────────────

    /**
     * Adds a BOOLEAN / INTEGER column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint bool(String name) {
        return col(name, dbType == DatabaseType.SQLITE ? "INTEGER" : "BOOLEAN");
    }

    // ─── DATE / TIME ─────────────────────────────────────────

    /**
     * Adds a DATE column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint date(String name) { return col(name, "DATE"); }

    /**
     * Adds a DATETIME / TIMESTAMP / TEXT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint dateTime(String name) {
        return col(name, switch (dbType) {
            case POSTGRESQL -> "TIMESTAMP";
            case MYSQL      -> "DATETIME";
            default         -> "TEXT";
        });
    }

    /**
     * Adds a TIMESTAMP column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint timestamp(String name) { return col(name, "TIMESTAMP"); }

    /**
     * Adds a TIME column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint time(String name) { return col(name, "TIME"); }

    /**
     * Adds created_at and updated_at columns with appropriate defaults.
     *
     * @return this blueprint
     */
    public Blueprint timestamps() {
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
     * Adds a nullable deleted_at column for soft deletes.
     *
     * @return this blueprint
     */
    public Blueprint softDeletes() {
        dateTime("deleted_at");
        nullable();
        return this;
    }

    // ─── JSON / BLOB ─────────────────────────────────────────

    /**
     * Adds a JSON / TEXT column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint json(String name) {
        return col(name, dbType == DatabaseType.SQLITE ? "TEXT" : "JSON");
    }

    /**
     * Adds a BLOB column.
     *
     * @param name column name
     * @return this blueprint
     */
    public Blueprint blob(String name) { return col(name, "BLOB"); }

    // ─── ENUM ────────────────────────────────────────────────

    /**
     * Adds an ENUM / CHECK column.
     *
     * @param name   column name
     * @param values allowed enum values
     * @return this blueprint
     */
    public Blueprint enumCol(String name, String... values) {
        if (dbType == DatabaseType.MYSQL) {
            col(name, "ENUM('" + String.join("', '", values) + "')");
        } else {
            col(name, "VARCHAR(50)");
            constraints.add("CHECK (" + name + " IN ('" + String.join("', '", values) + "'))");
        }
        return this;
    }

    // ─── MODIFIERS ───────────────────────────────────────────

    /** Appends NOT NULL to the last column. */
    public Blueprint notNull()                   { modifyLast(" NOT NULL");           return this; }
    /** Appends UNIQUE to the last column. */
    public Blueprint unique()                    { modifyLast(" UNIQUE");             return this; }
    /** Columns are nullable by default — no-op for readability. */
    public Blueprint nullable()                  { return this; }
    /** @param value default value string */
    public Blueprint defaultValue(String value)  { modifyLast(" DEFAULT " + value);  return this; }
    /** @param value default integer value */
    public Blueprint defaultValue(int value)     { modifyLast(" DEFAULT " + value);  return this; }
    /** @param value default boolean value (stored as 1 or 0) */
    public Blueprint defaultValue(boolean value) { modifyLast(" DEFAULT " + (value ? "1" : "0")); return this; }

    // ─── FOREIGN KEYS ────────────────────────────────────────

    /**
     * Adds a FOREIGN KEY constraint referencing the last column.
     *
     * @param refTable  referenced table name
     * @param refColumn referenced column name
     * @return this blueprint
     */
    public Blueprint foreignKey(String refTable, String refColumn) {
        if (!columns.isEmpty()) {
            String colName = columns.get(columns.size() - 1).split("\\s+")[0];
            fk(refTable, refColumn, colName);
        }
        return this;
    }

    /** Appends ON DELETE CASCADE to the last foreign key. */
    public Blueprint cascadeOnDelete()  { modifyLastConstraint(" ON DELETE CASCADE");  return this; }
    /** Appends ON DELETE SET NULL to the last foreign key. */
    public Blueprint nullOnDelete()     { modifyLastConstraint(" ON DELETE SET NULL"); return this; }
    /** Appends ON DELETE RESTRICT to the last foreign key. */
    public Blueprint restrictOnDelete() { modifyLastConstraint(" ON DELETE RESTRICT"); return this; }
    /** Appends ON UPDATE CASCADE to the last foreign key. */
    public Blueprint cascadeOnUpdate()  { modifyLastConstraint(" ON UPDATE CASCADE");  return this; }

    // ─── INDEXES ─────────────────────────────────────────────

    /**
     * Adds a composite UNIQUE constraint.
     *
     * @param columnNames columns forming the unique index
     * @return this blueprint
     */
    public Blueprint uniqueIndex(String... columnNames) {
        for (String c : columnNames) SqlIdentifier.requireIdentifier(c);
        constraints.add("UNIQUE (" + String.join(", ", columnNames) + ")");
        return this;
    }

    /**
     * Adds name_id (BIGINT NOT NULL) and name_type (VARCHAR NOT NULL) columns for polymorphic relations.
     *
     * @param name morph base name
     * @return this blueprint
     */
    public Blueprint morphs(String name) {
        bigInteger(name + "_id").notNull();
        string(name + "_type").notNull();
        return this;
    }

    // ─── ACCESSORS ───────────────────────────────────────────

    /** @return column definitions list */
    public List<String> getColumns()     { return columns; }

    /** @return constraint definitions list */
    public List<String> getConstraints() { return constraints; }
}