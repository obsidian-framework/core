package com.obsidian.core.database;

import com.obsidian.core.database.Migration.Blueprint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintTest
{
    private Blueprint blueprint(String dbType) {
        return new Blueprint(new ArrayList<>(), dbType);
    }

    private Blueprint blueprint(List<String> columns, String dbType) {
        return new Blueprint(columns, dbType);
    }

    // ──────────────────────────────────────────────
    // id()
    // ──────────────────────────────────────────────

    @Test
    void id_sqlite() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "sqlite").id();

        assertEquals(1, cols.size());
        assertEquals("id INTEGER PRIMARY KEY AUTOINCREMENT", cols.get(0));
    }

    @Test
    void id_mysql() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").id();

        assertEquals("id INT AUTO_INCREMENT PRIMARY KEY", cols.get(0));
    }

    @Test
    void id_postgresql() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "postgresql").id();

        assertEquals("id SERIAL PRIMARY KEY", cols.get(0));
    }

    @Test
    void id_customName() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").id("user_id");

        assertEquals("user_id INT AUTO_INCREMENT PRIMARY KEY", cols.get(0));
    }

    // ──────────────────────────────────────────────
    // string()
    // ──────────────────────────────────────────────

    @Test
    void string_sqlite_usesText() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "sqlite").string("name");

        assertEquals("name TEXT", cols.get(0));
    }

    @Test
    void string_mysql_usesVarchar255() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").string("name");

        assertEquals("name VARCHAR(255)", cols.get(0));
    }

    @Test
    void string_customLength() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "postgresql").string("email", 100);

        assertEquals("email VARCHAR(100)", cols.get(0));
    }

    // ──────────────────────────────────────────────
    // integer / bigInteger / decimal
    // ──────────────────────────────────────────────

    @Test
    void integer_postgresql_usesINTEGER() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "postgresql").integer("age");

        assertEquals("age INTEGER", cols.get(0));
    }

    @Test
    void integer_mysql_usesINT() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").integer("age");

        assertEquals("age INT", cols.get(0));
    }

    @Test
    void bigInteger() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").bigInteger("total");

        assertEquals("total BIGINT", cols.get(0));
    }

    @Test
    void decimal() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").decimal("price", 10, 2);

        assertEquals("price DECIMAL(10,2)", cols.get(0));
    }

    // ──────────────────────────────────────────────
    // bool
    // ──────────────────────────────────────────────

    @Test
    void bool_sqlite_usesInteger() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "sqlite").bool("active");

        assertEquals("active INTEGER", cols.get(0));
    }

    @Test
    void bool_mysql_usesBoolean() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").bool("active");

        assertEquals("active BOOLEAN", cols.get(0));
    }

    // ──────────────────────────────────────────────
    // dateTime
    // ──────────────────────────────────────────────

    @Test
    void dateTime_sqlite_usesText() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "sqlite").dateTime("created");

        assertEquals("created TEXT", cols.get(0));
    }

    @Test
    void dateTime_mysql_usesDatetime() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").dateTime("created");

        assertEquals("created DATETIME", cols.get(0));
    }

    @Test
    void dateTime_postgresql_usesTimestamp() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "postgresql").dateTime("created");

        assertEquals("created TIMESTAMP", cols.get(0));
    }

    // ──────────────────────────────────────────────
    // timestamps()
    // ──────────────────────────────────────────────

    @Test
    void timestamps_mysql() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").timestamps();

        assertEquals(2, cols.size());
        assertTrue(cols.get(0).contains("created_at"));
        assertTrue(cols.get(1).contains("ON UPDATE CURRENT_TIMESTAMP"));
    }

    @Test
    void timestamps_sqlite_usesText() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "sqlite").timestamps();

        assertEquals(2, cols.size());
        assertTrue(cols.get(0).startsWith("created_at TEXT"));
        assertTrue(cols.get(1).startsWith("updated_at TEXT"));
    }

    // ──────────────────────────────────────────────
    // Modifiers: notNull, unique, defaultValue
    // ──────────────────────────────────────────────

    @Test
    void notNull_appendsToLastColumn() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").string("email").notNull();

        assertEquals("email VARCHAR(255) NOT NULL", cols.get(0));
    }

    @Test
    void unique_appendsToLastColumn() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").string("email").unique();

        assertEquals("email VARCHAR(255) UNIQUE", cols.get(0));
    }

    @Test
    void defaultValue_appendsToLastColumn() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").integer("status").defaultValue("0");

        assertEquals("status INT DEFAULT 0", cols.get(0));
    }

    @Test
    void chained_modifiers() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").string("email").notNull().unique();

        assertEquals("email VARCHAR(255) NOT NULL UNIQUE", cols.get(0));
    }

    @Test
    void nullable_isNoOp() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql").string("bio").nullable();

        assertEquals("bio VARCHAR(255)", cols.get(0));
    }

    // ──────────────────────────────────────────────
    // Full table scenario
    // ──────────────────────────────────────────────

    @Test
    void fullTable_mysql() {
        List<String> cols = new ArrayList<>();
        blueprint(cols, "mysql")
                .id()
                .string("username").notNull().unique()
                .string("email", 100).notNull()
                .bool("active").defaultValue("1")
                .timestamps();

        assertEquals(6, cols.size());
        assertEquals("id INT AUTO_INCREMENT PRIMARY KEY", cols.get(0));
        assertEquals("username VARCHAR(255) NOT NULL UNIQUE", cols.get(1));
        assertEquals("email VARCHAR(100) NOT NULL", cols.get(2));
        assertEquals("active BOOLEAN DEFAULT 1", cols.get(3));
    }

    @Test
    void notNull_onEmptyColumns_doesNothing() {
        List<String> cols = new ArrayList<>();
        // Should not throw
        blueprint(cols, "mysql").notNull();
        assertTrue(cols.isEmpty());
    }
}