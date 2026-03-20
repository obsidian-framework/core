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

    /**
     * Value.
     *
     * @return The string value
     */
    public String value() {
        return value;
    }

    /**
     * From String.
     *
     * @param value The value to compare against
     * @return This instance for method chaining
     */
    public static DatabaseType fromString(String value) {
        if (value == null || value.isBlank()) return SQLITE;
        for (DatabaseType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) return type;
        }
        return SQLITE;
    }
}
