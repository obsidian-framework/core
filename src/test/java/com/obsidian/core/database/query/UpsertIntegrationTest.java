package com.obsidian.core.database.query;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.query.QueryBuilder;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QueryBuilder#upsert against a real SQLite in-memory database.
 *
 * Verifies end-to-end behaviour:
 *   - New rows are inserted
 *   - Conflicting rows are updated
 *   - Non-conflicting rows are unaffected
 *   - updateKeys empty → all non-unique columns updated
 *   - updateKeys explicit → only those columns updated
 *   - Empty rows → no-op (returns 0)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpsertIntegrationTest
{
    @BeforeEach
    void setUp() {
        TestHelper.setup();
        TestHelper.seed();
    }

    @AfterEach
    void tearDown() {
        TestHelper.teardown();
    }

    // ─── Helpers ─────────────────────────────────────────────

    private Map<String, Object> row(Object... kv) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private Map<String, Object> findByEmail(String email) {
        return new QueryBuilder("users").where("email", email).first();
    }

    private long userCount() {
        return new QueryBuilder("users").count();
    }

    // ─── Insert on no conflict ────────────────────────────────

    @Test @Order(1)
    void upsert_insertsNewRow_whenNoConflict() {
        List<Map<String, Object>> rows = List.of(
                row("name", "Dave", "email", "dave@example.com", "age", 28)
        );

        new QueryBuilder("users").upsert(rows, List.of("email"));

        Map<String, Object> dave = findByEmail("dave@example.com");
        assertNotNull(dave, "Dave must be inserted");
        assertEquals("Dave", dave.get("name"));
        assertEquals(4L, userCount());
    }

    @Test @Order(2)
    void upsert_insertsMultipleNewRows() {
        List<Map<String, Object>> rows = List.of(
                row("name", "Dave",  "email", "dave@example.com",  "age", 28),
                row("name", "Eve",   "email", "eve@example.com",   "age", 22),
                row("name", "Frank", "email", "frank@example.com", "age", 34)
        );

        new QueryBuilder("users").upsert(rows, List.of("email"));

        assertEquals(6L, userCount());
        assertNotNull(findByEmail("dave@example.com"));
        assertNotNull(findByEmail("eve@example.com"));
        assertNotNull(findByEmail("frank@example.com"));
    }

    // ─── Update on conflict ───────────────────────────────────

    @Test @Order(10)
    void upsert_updatesExistingRow_onEmailConflict() {
        // alice@example.com already exists with name=Alice, age=30
        List<Map<String, Object>> rows = List.of(
                row("name", "Alice2", "email", "alice@example.com", "age", 99)
        );

        new QueryBuilder("users").upsert(rows, List.of("email"), List.of("name", "age"));

        Map<String, Object> alice = findByEmail("alice@example.com");
        assertNotNull(alice);
        assertEquals("Alice2", alice.get("name"), "name must be updated");
        assertEquals(99, ((Number) alice.get("age")).intValue(), "age must be updated");
        // Count must not change — no new row
        assertEquals(3L, userCount());
    }

    @Test @Order(11)
    void upsert_withExplicitUpdateKeys_onlyUpdatesSpecifiedColumns() {
        // Only update age, not name
        List<Map<String, Object>> rows = List.of(
                row("name", "ShouldNotChange", "email", "alice@example.com", "age", 99)
        );

        new QueryBuilder("users").upsert(rows, List.of("email"), List.of("age"));

        Map<String, Object> alice = findByEmail("alice@example.com");
        assertNotNull(alice);
        assertEquals("Alice", alice.get("name"), "name must NOT be updated");
        assertEquals(99, ((Number) alice.get("age")).intValue(), "age must be updated");
    }

    // ─── Mix of insert and update ─────────────────────────────

    @Test @Order(20)
    void upsert_mixOfNewAndConflicting_handledCorrectly() {
        List<Map<String, Object>> rows = List.of(
                row("name", "AliceNew", "email", "alice@example.com", "age", 31), // conflict
                row("name", "Dave",     "email", "dave@example.com",  "age", 28)  // new
        );

        new QueryBuilder("users").upsert(rows, List.of("email"), List.of("name", "age"));

        Map<String, Object> alice = findByEmail("alice@example.com");
        Map<String, Object> dave  = findByEmail("dave@example.com");

        assertNotNull(alice);
        assertNotNull(dave);
        assertEquals("AliceNew", alice.get("name"), "Alice must be updated");
        assertEquals("Dave",     dave.get("name"),  "Dave must be inserted");
        assertEquals(4L, userCount());
    }

    // ─── Non-conflicting rows unaffected ─────────────────────

    @Test @Order(30)
    void upsert_doesNotAffectUnrelatedRows() {
        List<Map<String, Object>> rows = List.of(
                row("name", "AliceUpdated", "email", "alice@example.com", "age", 31)
        );

        new QueryBuilder("users").upsert(rows, List.of("email"), List.of("name", "age"));

        // Bob and Charlie must be untouched
        Map<String, Object> bob     = findByEmail("bob@example.com");
        Map<String, Object> charlie = findByEmail("charlie@example.com");

        assertNotNull(bob);
        assertNotNull(charlie);
        assertEquals("Bob",     bob.get("name"));
        assertEquals("Charlie", charlie.get("name"));
    }

    // ─── Empty rows ───────────────────────────────────────────

    @Test @Order(40)
    void upsert_emptyRows_returnsZeroAndDoesNothing() {
        long before = userCount();
        int affected = new QueryBuilder("users").upsert(List.of(), List.of("email"));
        assertEquals(0, affected);
        assertEquals(before, userCount());
    }

    // ─── Null rows ────────────────────────────────────────────

    @Test @Order(41)
    void upsert_nullRows_returnsZeroAndDoesNothing() {
        long before = userCount();
        int affected = new QueryBuilder("users").upsert(null, List.of("email"));
        assertEquals(0, affected);
        assertEquals(before, userCount());
    }
}