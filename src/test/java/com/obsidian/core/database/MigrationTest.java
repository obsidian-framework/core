package com.obsidian.core.database;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Migration — Blueprint, createTable, dropTable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationTest
{

    @BeforeEach void setUp()    { TestHelper.setup(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    @Test @Order(1)
    void testCreateTableWithBlueprint() {
        Migration migration = new Migration() {
            @Override public void up() {
                createTable("test_table", table -> {
                    table.id();
                    table.string("name").notNull();
                    table.string("code", 10).unique();
                    table.text("description");
                    table.integer("count").defaultValue(0);
                    table.bool("active").defaultValue(true);
                    table.decimal("price", 10, 2);
                    table.timestamps();
                });
            }
            @Override public void down() { dropTable("test_table"); }
        };
        migration.type   = DatabaseType.SQLITE;
        migration.logger = org.slf4j.LoggerFactory.getLogger(MigrationTest.class);

        migration.up();

        DB.exec("INSERT INTO test_table (name, code, description, price) VALUES (?, ?, ?, ?)",
                "Test", "TST", "A test", 9.99);

        Map<String, Object> row = DB.firstRow("SELECT * FROM test_table WHERE name = ?", "Test");
        assertNotNull(row);
        assertEquals("TST", row.get("code"));

        migration.down();

        assertThrows(RuntimeException.class, () -> DB.findAll("SELECT * FROM test_table"));
    }

    @Test @Order(2)
    void testBlueprintForeignKey() {
        Migration migration = new Migration() {
            @Override public void up() {
                createTable("fk_test", table -> {
                    table.id();
                    table.integer("user_id").notNull().foreignKey("users", "id").cascadeOnDelete();
                    table.string("data");
                });
            }
            @Override public void down() { dropTable("fk_test"); }
        };
        migration.type   = DatabaseType.SQLITE;
        migration.logger = org.slf4j.LoggerFactory.getLogger(MigrationTest.class);

        migration.up();

        DB.exec("INSERT INTO fk_test (user_id, data) VALUES (?, ?)", 1, "test data");
        Map<String, Object> row = DB.firstRow("SELECT * FROM fk_test WHERE user_id = ?", 1);
        assertNotNull(row);
        assertEquals("test data", row.get("data"));

        migration.down();
    }

    @Test @Order(3)
    void testBlueprintSoftDeletes() {
        Migration migration = new Migration() {
            @Override public void up() {
                createTable("soft_test", table -> {
                    table.id();
                    table.string("name");
                    table.softDeletes();
                    table.timestamps();
                });
            }
            @Override public void down() { dropTable("soft_test"); }
        };
        migration.type   = DatabaseType.SQLITE;
        migration.logger = org.slf4j.LoggerFactory.getLogger(MigrationTest.class);

        migration.up();

        DB.exec("INSERT INTO soft_test (name) VALUES (?)", "test");
        Map<String, Object> row = DB.firstRow("SELECT * FROM soft_test");
        assertNotNull(row);
        assertNull(row.get("deleted_at"));

        migration.down();
    }

    @Test @Order(4)
    void testBlueprintJson() {
        Migration migration = new Migration() {
            @Override public void up() {
                createTable("json_test", table -> {
                    table.id();
                    table.json("data");
                });
            }
            @Override public void down() { dropTable("json_test"); }
        };
        migration.type   = DatabaseType.SQLITE;
        migration.logger = org.slf4j.LoggerFactory.getLogger(MigrationTest.class);

        migration.up();

        DB.exec("INSERT INTO json_test (data) VALUES (?)", "{\"key\": \"value\"}");
        Map<String, Object> row = DB.firstRow("SELECT * FROM json_test");
        assertNotNull(row);
        assertEquals("{\"key\": \"value\"}", row.get("data"));

        migration.down();
    }

    @Test @Order(5)
    void testTableExists() {
        Migration migration = new Migration() {
            @Override public void up() {}
            @Override public void down() {}
        };
        migration.type   = DatabaseType.SQLITE;
        migration.logger = org.slf4j.LoggerFactory.getLogger(MigrationTest.class);

        assertTrue(migration.tableExists("users"));
        assertFalse(migration.tableExists("nonexistent_table_xyz"));
    }
}