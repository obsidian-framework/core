package com.obsidian.core.database.query;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.query.QueryBuilder;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryBuilder#insertAll.
 *
 * Covers the two bugs fixed in the audit:
 *   1. Column names from row 0 must be validated before the batch runs.
 *   2. batchColumns must be anchored to row 0's Map order so values are
 *      never silently bound to the wrong column.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InsertAllTest
{

    @BeforeEach void setUp()    { TestHelper.setup(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ─── HAPPY PATH ──────────────────────────────────────────

    @Test @Order(1)
    void insertAll_insertsAllRows() {
        List<Map<String, Object>> rows = List.of(
                Map.of("name", "Dave",  "email", "dave@example.com",  "age", 28),
                Map.of("name", "Eve",   "email", "eve@example.com",   "age", 22),
                Map.of("name", "Frank", "email", "frank@example.com", "age", 34)
        );

        new QueryBuilder("users").insertAll(rows);

        long count = new QueryBuilder("users").count();
        assertEquals(3, count);
    }

    @Test @Order(2)
    void insertAll_singleRowDelegatesToInsert() {
        List<Map<String, Object>> rows = List.of(
                Map.of("name", "Solo", "email", "solo@example.com", "age", 20)
        );

        new QueryBuilder("users").insertAll(rows);

        Map<String, Object> row = new QueryBuilder("users").where("name", "Solo").first();
        assertNotNull(row);
        assertEquals("solo@example.com", row.get("email"));
    }

    @Test @Order(3)
    void insertAll_emptyListIsNoOp() {
        assertDoesNotThrow(() -> new QueryBuilder("users").insertAll(List.of()));
        assertEquals(0, new QueryBuilder("users").count());
    }

    @Test @Order(4)
    void insertAll_nullListIsNoOp() {
        assertDoesNotThrow(() -> new QueryBuilder("users").insertAll(null));
    }

    @Test @Order(5)
    void insertAll_valuesAreCorrectlyBound() {
        // Deliberately construct rows with keys in different insertion orders.
        // After the fix, batchColumns is anchored to row 0's keySet order,
        // so all values must still land in the correct columns.
        Map<String, Object> row0 = new LinkedHashMap<>();
        row0.put("name",  "OrderA");
        row0.put("email", "a@example.com");
        row0.put("age",   10);

        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name",  "OrderB");
        row1.put("email", "b@example.com");
        row1.put("age",   20);

        new QueryBuilder("users").insertAll(List.of(row0, row1));

        Map<String, Object> a = new QueryBuilder("users").where("name", "OrderA").first();
        Map<String, Object> b = new QueryBuilder("users").where("name", "OrderB").first();

        assertNotNull(a, "OrderA should exist");
        assertNotNull(b, "OrderB should exist");
        assertEquals("a@example.com", a.get("email"), "OrderA email must not be swapped");
        assertEquals(10, ((Number) a.get("age")).intValue(),  "OrderA age must not be swapped");
        assertEquals("b@example.com", b.get("email"), "OrderB email must not be swapped");
        assertEquals(20, ((Number) b.get("age")).intValue(),  "OrderB age must not be swapped");
    }

    // ─── COLUMN VALIDATION (bug fix #1) ──────────────────────

    @Test @Order(10)
    void insertAll_rejectsInvalidColumnNameInRow0() {
        List<Map<String, Object>> rows = List.of(
                Map.of("name; DROP TABLE users--", "injected", "email", "x@x.com")
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QueryBuilder("users").insertAll(rows)
        );
        assertTrue(ex.getMessage().contains("invalid identifier"),
                "Exception must mention injection guard");
    }

    @Test @Order(11)
    void insertAll_rejectsInvalidColumnNameInLaterRow() {
        // Row 0 is valid. Row 1 has a different key set — must be caught by the
        // key-set equality check before any SQL is executed.
        Map<String, Object> row0 = Map.of("name", "Good", "email", "g@g.com");
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name",  "Bad");
        row1.put("email", "b@b.com");
        row1.put("name; DROP TABLE users--", "injected"); // extra key → different key set

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QueryBuilder("users").insertAll(List.of(row0, row1))
        );
        assertTrue(ex.getMessage().contains("different key set"),
                "Exception must mention key set mismatch");
    }

    // ─── KEY SET CONSISTENCY ─────────────────────────────────

    @Test @Order(20)
    void insertAll_rejectsMismatchedKeySet() {
        Map<String, Object> row0 = Map.of("name", "A", "email", "a@a.com", "age", 1);
        Map<String, Object> row1 = Map.of("name", "B", "email", "b@b.com"); // missing "age"

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new QueryBuilder("users").insertAll(List.of(row0, row1))
        );
        assertTrue(ex.getMessage().contains("different key set"));
    }

    @Test @Order(21)
    void insertAll_rejectsExtraKeyInLaterRow() {
        Map<String, Object> row0 = Map.of("name", "A", "email", "a@a.com");
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name",  "B");
        row1.put("email", "b@b.com");
        row1.put("age",   99); // extra key not in row 0

        assertThrows(IllegalArgumentException.class,
                () -> new QueryBuilder("users").insertAll(List.of(row0, row1)));
    }
}