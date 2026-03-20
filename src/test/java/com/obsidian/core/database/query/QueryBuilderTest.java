package com.obsidian.core.database.query;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.QueryLog;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryBuilder — SQL compilation and execution.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryBuilderTest {

    @BeforeEach void setUp() { TestHelper.setup(); TestHelper.seed(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ─── SQL COMPILATION ─────────────────────────────────────

    @Test @Order(1)
    void testBasicSelect() {
        String sql = new QueryBuilder("users").select("id", "name").toSql();
        assertEquals("SELECT id, name FROM users", sql);
    }

    @Test @Order(2)
    void testSelectAll() {
        String sql = new QueryBuilder("users").toSql();
        assertEquals("SELECT * FROM users", sql);
    }

    @Test @Order(3)
    void testDistinct() {
        String sql = new QueryBuilder("users").distinct().select("role").toSql();
        assertEquals("SELECT DISTINCT role FROM users", sql);
    }

    @Test @Order(4)
    void testWhereCompilation() {
        String sql = new QueryBuilder("users")
                .where("active", "=", 1)
                .where("age", ">", 18)
                .toSql();
        assertEquals("SELECT * FROM users WHERE active = ? AND age > ?", sql);
    }

    @Test @Order(5)
    void testOrWhere() {
        String sql = new QueryBuilder("users")
                .where("role", "admin")
                .orWhere("role", "editor")
                .toSql();
        assertEquals("SELECT * FROM users WHERE role = ? OR role = ?", sql);
    }

    @Test @Order(6)
    void testWhereNull() {
        String sql = new QueryBuilder("users").whereNull("deleted_at").toSql();
        assertEquals("SELECT * FROM users WHERE deleted_at IS NULL", sql);
    }

    @Test @Order(7)
    void testWhereIn() {
        String sql = new QueryBuilder("users")
                .whereIn("id", List.of(1, 2, 3))
                .toSql();
        assertEquals("SELECT * FROM users WHERE id IN (?, ?, ?)", sql);
    }

    @Test @Order(8)
    void testWhereBetween() {
        String sql = new QueryBuilder("users")
                .whereBetween("age", 18, 30)
                .toSql();
        assertEquals("SELECT * FROM users WHERE age BETWEEN ? AND ?", sql);
    }

    @Test @Order(9)
    void testNestedWhere() {
        String sql = new QueryBuilder("users")
                .where("active", 1)
                .where(q -> q.where("role", "admin").orWhere("age", ">", 30))
                .toSql();
        assertEquals("SELECT * FROM users WHERE active = ? AND (role = ? OR age > ?)", sql);
    }

    @Test @Order(10)
    void testJoin() {
        String sql = new QueryBuilder("users")
                .select("users.*", "profiles.bio")
                .join("profiles", "users.id", "=", "profiles.user_id")
                .toSql();
        assertEquals("SELECT users.*, profiles.bio FROM users INNER JOIN profiles ON users.id = profiles.user_id", sql);
    }

    @Test @Order(11)
    void testOrderByGroupByLimit() {
        String sql = new QueryBuilder("users")
                .orderBy("name", "ASC")
                .groupBy("role")
                .limit(10)
                .offset(5)
                .toSql();
        assertEquals("SELECT * FROM users GROUP BY role ORDER BY name ASC LIMIT 10 OFFSET 5", sql);
    }

    // ─── EXECUTION ───────────────────────────────────────────

    @Test @Order(20)
    void testGet() {
        List<Map<String, Object>> users = new QueryBuilder("users").get();
        assertEquals(3, users.size());
    }

    @Test @Order(21)
    void testFirst() {
        Map<String, Object> user = new QueryBuilder("users").where("name", "Alice").first();
        assertNotNull(user);
        assertEquals("Alice", user.get("name"));
    }

    @Test @Order(22)
    void testFirstReturnsNull() {
        Map<String, Object> user = new QueryBuilder("users").where("name", "Nobody").first();
        assertNull(user);
    }

    @Test @Order(23)
    void testWhereExecution() {
        List<Map<String, Object>> active = new QueryBuilder("users")
                .where("active", 1)
                .get();
        assertEquals(2, active.size());
    }

    @Test @Order(24)
    void testCount() {
        long count = new QueryBuilder("users").count();
        assertEquals(3, count);
    }

    @Test @Order(25)
    void testCountWithWhere() {
        long count = new QueryBuilder("users").where("active", 1).count();
        assertEquals(2, count);
    }

    @Test @Order(26)
    void testMax() {
        Object maxAge = new QueryBuilder("users").max("age");
        assertNotNull(maxAge);
        assertEquals(35, ((Number) maxAge).intValue());
    }

    @Test @Order(27)
    void testSum() {
        Object sum = new QueryBuilder("users").sum("age");
        assertNotNull(sum);
        assertEquals(90, ((Number) sum).intValue());
    }

    @Test @Order(28)
    void testPluck() {
        List<Object> names = new QueryBuilder("users").orderBy("name").pluck("name");
        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test @Order(29)
    void testExists() {
        assertTrue(new QueryBuilder("users").where("name", "Alice").exists());
        assertFalse(new QueryBuilder("users").where("name", "Nobody").exists());
    }

    // ─── INSERT / UPDATE / DELETE ────────────────────────────

    @Test @Order(30)
    void testInsert() {
        Object id = new QueryBuilder("users").insert(Map.of(
                "name", "Dave", "email", "dave@example.com", "age", 28));
        assertNotNull(id);

        long count = new QueryBuilder("users").count();
        assertEquals(4, count);
    }

    @Test @Order(31)
    void testUpdate() {
        int affected = new QueryBuilder("users")
                .where("name", "Alice")
                .update(Map.of("age", 31));
        assertEquals(1, affected);

        Map<String, Object> alice = new QueryBuilder("users").where("name", "Alice").first();
        assertEquals(31, ((Number) alice.get("age")).intValue());
    }

    @Test @Order(32)
    void testDelete() {
        int affected = new QueryBuilder("users").where("name", "Charlie").delete();
        assertEquals(1, affected);

        long count = new QueryBuilder("users").count();
        assertEquals(2, count);
    }

    @Test @Order(33)
    void testIncrement() {
        new QueryBuilder("users").where("name", "Alice").increment("age", 5);
        Map<String, Object> alice = new QueryBuilder("users").where("name", "Alice").first();
        assertEquals(35, ((Number) alice.get("age")).intValue());
    }

    @Test @Order(34)
    void testDecrement() {
        new QueryBuilder("users").where("name", "Alice").decrement("age", 2);
        Map<String, Object> alice = new QueryBuilder("users").where("name", "Alice").first();
        assertEquals(28, ((Number) alice.get("age")).intValue());
    }

    // ─── PAGINATION ──────────────────────────────────────────

    @Test @Order(40)
    void testForPage() {
        List<Map<String, Object>> page1 = new QueryBuilder("users").orderBy("id").forPage(1, 2).get();
        assertEquals(2, page1.size());
        assertEquals("Alice", page1.get(0).get("name"));

        List<Map<String, Object>> page2 = new QueryBuilder("users").orderBy("id").forPage(2, 2).get();
        assertEquals(1, page2.size());
        assertEquals("Charlie", page2.get(0).get("name"));
    }

    // ─── REMOVE WHERE NULL ───────────────────────────────────

    @Test @Order(50)
    void testRemoveWhereNull() {
        QueryBuilder qb = new QueryBuilder("users")
                .whereNull("deleted_at")
                .where("active", 1);

        String before = qb.toSql();
        assertTrue(before.contains("deleted_at IS NULL"));

        qb.removeWhereNull("deleted_at");
        String after = qb.toSql();
        assertFalse(after.contains("deleted_at IS NULL"));
        assertTrue(after.contains("active = ?"));
    }

    // ─── QUERY LOG ───────────────────────────────────────────

    @Test @Order(60)
    void testQueryLog() {
        QueryLog.enable();
        QueryLog.clear();

        new QueryBuilder("users").get();
        new QueryBuilder("users").where("active", 1).count();

        assertEquals(2, QueryLog.count());
        assertTrue(QueryLog.getLog().get(0).getSql().contains("SELECT"));
        assertTrue(QueryLog.totalTimeMs() >= 0);

        QueryLog.disable();
        QueryLog.clear();
    }

    @Test @Order(61)
    void testQueryLogDisabled() {
        QueryLog.disable();
        QueryLog.clear();

        new QueryBuilder("users").get();

        assertEquals(0, QueryLog.count());
    }

    // ─── AGGREGATE DOES NOT MUTATE BUILDER ───────────────────

    @Test @Order(70)
    void testCountDoesNotMutateBuilder() {
        QueryBuilder qb = new QueryBuilder("users").where("active", 1);

        // Count should not corrupt columns or limit
        long count = qb.count();
        assertEquals(2, count);

        // Subsequent get() should still work correctly
        List<Map<String, Object>> users = qb.get();
        assertEquals(2, users.size());
        assertNotNull(users.get(0).get("name")); // columns not replaced by COUNT(*)
    }
}
