package com.obsidian.core.database;

import com.obsidian.core.database.DB;
import com.obsidian.core.database.DatabaseType;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DB — connection management, raw SQL, transactions.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DBTest {

    @BeforeEach void setUp() { TestHelper.setup(); TestHelper.seed(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ─── CONNECTION ──────────────────────────────────────────

    @Test @Order(1)
    void testGetInstance() {
        DB db = DB.getInstance();
        assertNotNull(db);
        assertEquals(DatabaseType.SQLITE, db.getType());
    }

    @Test @Order(2)
    void testHasConnection() {
        assertTrue(DB.hasConnection());
    }

    @Test @Order(3)
    void testGetConnection() {
        assertNotNull(DB.getConnection());
    }

    // ─── RAW SQL ─────────────────────────────────────────────

    @Test @Order(10)
    void testExec() {
        DB.exec("INSERT INTO users (name, email) VALUES (?, ?)", "Test", "test@example.com");
        long count = ((Number) DB.firstCell("SELECT COUNT(*) FROM users")).longValue();
        assertEquals(4, count);
    }

    @Test @Order(11)
    void testFindAll() {
        List<Map<String, Object>> users = DB.findAll("SELECT * FROM users ORDER BY id");
        assertEquals(3, users.size());
        assertEquals("Alice", users.get(0).get("name"));
    }

    @Test @Order(12)
    void testFindAllWithParams() {
        List<Map<String, Object>> users = DB.findAll("SELECT * FROM users WHERE role = ?", "admin");
        assertEquals(1, users.size());
    }

    @Test @Order(13)
    void testFirstCell() {
        Object count = DB.firstCell("SELECT COUNT(*) FROM users");
        assertNotNull(count);
        assertEquals(3L, ((Number) count).longValue());
    }

    @Test @Order(14)
    void testFirstCellReturnsNull() {
        Object result = DB.firstCell("SELECT name FROM users WHERE id = ?", 999);
        assertNull(result);
    }

    @Test @Order(15)
    void testFirstRow() {
        Map<String, Object> row = DB.firstRow("SELECT * FROM users WHERE name = ?", "Bob");
        assertNotNull(row);
        assertEquals("Bob", row.get("name"));
        assertEquals("bob@example.com", row.get("email"));
    }

    @Test @Order(16)
    void testInsertAndGetKey() {
        Object key = DB.insertAndGetKey(
                "INSERT INTO users (name, email) VALUES (?, ?)", "KeyTest", "key@example.com");
        assertNotNull(key);
        assertTrue(((Number) key).longValue() > 0);
    }

    // ─── TRANSACTIONS ────────────────────────────────────────

    @Test @Order(20)
    void testTransactionCommit() {
        DB.withTransaction(() -> {
            DB.exec("INSERT INTO users (name, email) VALUES (?, ?)", "TX1", "tx1@example.com");
            DB.exec("INSERT INTO users (name, email) VALUES (?, ?)", "TX2", "tx2@example.com");
            return null;
        });

        long count = ((Number) DB.firstCell("SELECT COUNT(*) FROM users")).longValue();
        assertEquals(5, count);
    }

    @Test @Order(21)
    void testTransactionRollback() {
        try {
            DB.withTransaction(() -> {
                DB.exec("INSERT INTO users (name, email) VALUES (?, ?)", "TX_FAIL", "fail@example.com");
                throw new RuntimeException("Simulated failure");
            });
        } catch (RuntimeException e) {
            // Expected
        }

        // The insert should have been rolled back
        List<Map<String, Object>> rows = DB.findAll("SELECT * FROM users WHERE name = ?", "TX_FAIL");
        assertEquals(0, rows.size());
    }

    @Test @Order(22)
    void testWithConnectionAutoManages() {
        // Close current connection first
        DB.closeConnection();
        assertFalse(DB.hasConnection());

        // withConnection should open and close automatically
        String result = DB.withConnection(() -> {
            assertTrue(DB.hasConnection());
            return "OK";
        });

        assertEquals("OK", result);
    }

    // ─── ERROR HANDLING ──────────────────────────────────────

    @Test @Order(30)
    void testExecInvalidSqlThrows() {
        assertThrows(RuntimeException.class, () -> DB.exec("INVALID SQL BLAH"));
    }

    @Test @Order(31)
    void testFindAllInvalidSqlThrows() {
        assertThrows(RuntimeException.class, () -> DB.findAll("SELECT * FROM nonexistent_table"));
    }
}
