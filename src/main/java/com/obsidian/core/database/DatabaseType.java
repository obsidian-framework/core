package com.obsidian.core.database;

/**
 * Supported database types.
 */
public enum DatabaseType
{
    SQLITE("sqlite"),
    MYSQL("mysql"),
    POSTGRESQL("postgresql");

    private final String value;

    DatabaseType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Resolves a DatabaseType from a string value.
     * Defaults to SQLITE if null, empty, or unrecognized.
     *
     * @param value The string value (case-insensitive)
     * @return The matching DatabaseType
     */
    public static DatabaseType fromString(String value) {
        if (value == null || value.isBlank()) return SQLITE;
        for (DatabaseType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) return type;
        }
        return SQLITE;
    }
}