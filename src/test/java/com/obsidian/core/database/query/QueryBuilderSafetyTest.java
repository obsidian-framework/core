package com.obsidian.core.database.query;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.query.QueryBuilder;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryBuilder safety fixes:
 * - whereIn / whereNotIn with empty list
 * - forPage with invalid page / perPage
 * - update with empty values map
 * - requireWhere guard on update and delete
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryBuilderSafetyTest
{
    @BeforeEach void setUp()    { TestHelper.setup(); TestHelper.seed(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ─── whereIn empty list ──────────────────────────────────

    @Test @Order(1)
    void whereInEmptyListReturnsNoRows() {
        // IN () is invalid SQL — should short-circuit to 0 results, not crash
        List<Map<String, Object>> result = new QueryBuilder("users")
                .whereIn("id", Collections.emptyList())
                .get();
        assertTrue(result.isEmpty(), "whereIn([]) should match no rows");
    }

    @Test @Order(2)
    void whereInNullListReturnsNoRows() {
        List<Map<String, Object>> result = new QueryBuilder("users")
                .whereIn("id", null)
                .get();
        assertTrue(result.isEmpty(), "whereIn(null) should match no rows");
    }

    @Test @Order(3)
    void whereInEmptyListSqlIs1Eq0() {
        String sql = new QueryBuilder("users")
                .whereIn("id", Collections.emptyList())
                .toSql();
        assertTrue(sql.contains("1 = 0"),
                "whereIn([]) should compile to '1 = 0', got: " + sql);
    }

    @Test @Order(4)
    void whereInNonEmptyListWorksNormally() {
        List<Map<String, Object>> result = new QueryBuilder("users")
                .whereIn("id", List.of(1, 2))
                .get();
        assertEquals(2, result.size());
    }

    // ─── whereNotIn empty list ───────────────────────────────

    @Test @Order(10)
    void whereNotInEmptyListReturnsAllRows() {
        // NOT IN () is always true — should be a no-op
        List<Map<String, Object>> result = new QueryBuilder("users")
                .whereNotIn("id", Collections.emptyList())
                .get();
        assertEquals(3, result.size(), "whereNotIn([]) should match all rows");
    }

    @Test @Order(11)
    void whereNotInNullListReturnsAllRows() {
        List<Map<String, Object>> result = new QueryBuilder("users")
                .whereNotIn("id", null)
                .get();
        assertEquals(3, result.size(), "whereNotIn(null) should match all rows");
    }

    @Test @Order(12)
    void whereNotInEmptyListAddsNoWhereClause() {
        String sql = new QueryBuilder("users")
                .whereNotIn("id", Collections.emptyList())
                .toSql();
        assertFalse(sql.contains("NOT IN"),
                "whereNotIn([]) should add no WHERE clause, got: " + sql);
    }

    @Test @Order(13)
    void whereNotInNonEmptyListWorksNormally() {
        List<Map<String, Object>> result = new QueryBuilder("users")
                .whereNotIn("id", List.of(1))
                .get();
        assertEquals(2, result.size());
    }

    // ─── forPage validation ──────────────────────────────────

    @Test @Order(20)
    void forPageZeroThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new QueryBuilder("users").forPage(0, 10).get());
        assertTrue(ex.getMessage().contains("page must be >= 1"),
                "Should mention page constraint, got: " + ex.getMessage());
    }

    @Test @Order(21)
    void forPageNegativeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryBuilder("users").forPage(-1, 10).get());
    }

    @Test @Order(22)
    void forPageZeroPerPageThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new QueryBuilder("users").forPage(1, 0).get());
        assertTrue(ex.getMessage().contains("perPage must be >= 1"),
                "Should mention perPage constraint, got: " + ex.getMessage());
    }

    @Test @Order(23)
    void forPageNegativePerPageThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueryBuilder("users").forPage(1, -5).get());
    }

    @Test @Order(24)
    void forPageOneWorksNormally() {
        List<Map<String, Object>> page = new QueryBuilder("users")
                .orderBy("id")
                .forPage(1, 2)
                .get();
        assertEquals(2, page.size());
        assertEquals("Alice", page.get(0).get("name"));
    }

    @Test @Order(25)
    void forPageTwoWorksNormally() {
        List<Map<String, Object>> page = new QueryBuilder("users")
                .orderBy("id")
                .forPage(2, 2)
                .get();
        assertEquals(1, page.size());
        assertEquals("Charlie", page.get(0).get("name"));
    }

    // ─── update with empty map ───────────────────────────────

    @Test @Order(30)
    void updateEmptyMapReturnsZero() {
        int affected = new QueryBuilder("users")
                .where("name", "Alice")
                .update(Collections.emptyMap());
        assertEquals(0, affected, "update({}) should return 0 and not touch the DB");
    }

    @Test @Order(31)
    void updateNullMapReturnsZero() {
        int affected = new QueryBuilder("users")
                .where("name", "Alice")
                .update(null);
        assertEquals(0, affected, "update(null) should return 0 and not touch the DB");
    }

    @Test @Order(32)
    void updateEmptyMapDoesNotModifyRows() {
        new QueryBuilder("users")
                .where("name", "Alice")
                .update(Collections.emptyMap());

        Map<String, Object> alice = new QueryBuilder("users").where("name", "Alice").first();
        assertEquals(30, ((Number) alice.get("age")).intValue(),
                "Row should be untouched after update({})");
    }

    // ─── requireWhere on update ──────────────────────────────

    @Test @Order(40)
    void requireWhereOnUpdateWithoutWhereThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new QueryBuilder("users")
                        .requireWhere()
                        .update(Map.of("active", 0)));
        assertTrue(ex.getMessage().contains("users"),
                "Exception should mention the table name, got: " + ex.getMessage());
    }

    @Test @Order(41)
    void requireWhereOnUpdateWithWhereSucceeds() {
        int affected = new QueryBuilder("users")
                .requireWhere()
                .where("name", "Alice")
                .update(Map.of("age", 99));
        assertEquals(1, affected);

        Map<String, Object> alice = new QueryBuilder("users").where("name", "Alice").first();
        assertEquals(99, ((Number) alice.get("age")).intValue());
    }

    @Test @Order(42)
    void updateWithoutRequireWhereAndNoWhereStillWorks() {
        // Default behaviour unchanged — no requireWhere() = no guard
        int affected = new QueryBuilder("users").update(Map.of("active", 0));
        assertEquals(3, affected, "Without requireWhere(), full-table update is allowed");
    }

    // ─── requireWhere on delete ──────────────────────────────

    @Test @Order(50)
    void requireWhereOnDeleteWithoutWhereThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new QueryBuilder("users")
                        .requireWhere()
                        .delete());
        assertTrue(ex.getMessage().contains("users"),
                "Exception should mention the table name, got: " + ex.getMessage());
    }

    @Test @Order(51)
    void requireWhereOnDeleteWithWhereSucceeds() {
        int affected = new QueryBuilder("users")
                .requireWhere()
                .where("name", "Charlie")
                .delete();
        assertEquals(1, affected);
        assertEquals(2, new QueryBuilder("users").count());
    }

    @Test @Order(52)
    void deleteWithoutRequireWhereAndNoWhereStillWorks() {
        // Default behaviour unchanged
        int affected = new QueryBuilder("users").delete();
        assertEquals(3, affected, "Without requireWhere(), full-table delete is allowed");
        assertEquals(0, new QueryBuilder("users").count());
    }

    // ─── requireWhere does not interfere with normal queries ──

    @Test @Order(60)
    void requireWhereDoesNotAffectSelectGet() {
        // requireWhere() only guards update/delete — selects are unaffected
        List<Map<String, Object>> users = new QueryBuilder("users")
                .requireWhere()
                .get();
        assertEquals(3, users.size(),
                "requireWhere() should not block SELECT queries");
    }
}