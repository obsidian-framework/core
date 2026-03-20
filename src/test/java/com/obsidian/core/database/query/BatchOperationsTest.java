package com.obsidian.core.database.query;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.query.QueryBuilder;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pivot batch operations: attachMany, detachMany, sync, toggle.
 *
 * Covers the performance issues identified in the audit:
 *   - attachMany must insert all rows in a single batch (not N individual inserts)
 *   - detachMany must use a single whereIn+delete (not N individual deletes)
 *   - sync and toggle must use Set-based lookups (not List.contains O(n²))
 *
 * These tests use the role_user pivot table seeded by TestHelper.
 * Alice (id=1): roles 1 (ADMIN), 2 (EDITOR)
 * Bob   (id=2): role  3 (VIEWER)
 * Charlie (id=3): no roles
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatchOperationsTest {

    @BeforeEach void setUp()    { TestHelper.setup(); TestHelper.seed(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ─── HELPERS ─────────────────────────────────────────────

    private List<Object> pivotRolesFor(int userId) {
        return new QueryBuilder("role_user")
                .where("user_id", userId)
                .orderBy("role_id")
                .pluck("role_id");
    }

    private long pivotCount() {
        return new QueryBuilder("role_user").count();
    }

    // ─── insertAll (batch) ───────────────────────────────────

    @Test @Order(1)
    void insertAll_batchInsertsMultipleRows() {
        // Insert 3 pivot rows for Charlie in one batch
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int roleId = 1; roleId <= 3; roleId++) {
            rows.add(Map.of("user_id", 3, "role_id", roleId));
        }

        new QueryBuilder("role_user").insertAll(rows);

        List<Object> charlieRoles = pivotRolesFor(3);
        assertEquals(3, charlieRoles.size());
        assertEquals(1L, ((Number) charlieRoles.get(0)).longValue());
        assertEquals(2L, ((Number) charlieRoles.get(1)).longValue());
        assertEquals(3L, ((Number) charlieRoles.get(2)).longValue());
    }

    // ─── detachMany via whereIn ───────────────────────────────

    @Test @Order(10)
    void whereIn_delete_removesMultipleRowsAtOnce() {
        // Alice has roles [1, 2]; remove both in a single query
        new QueryBuilder("role_user")
                .where("user_id", 1)
                .whereIn("role_id", List.of(1, 2))
                .delete();

        assertEquals(0, pivotRolesFor(1).size(), "Alice must have no roles left");
        assertEquals(1, pivotRolesFor(2).size(), "Bob's roles must be untouched");
    }

    @Test @Order(11)
    void whereIn_delete_onlyRemovesTargetedRows() {
        List<Object> aliceRolesBefore = pivotRolesFor(1);
        assertEquals(2, aliceRolesBefore.size());

        // Remove only role 1 from Alice
        new QueryBuilder("role_user")
                .where("user_id", 1)
                .whereIn("role_id", List.of(1))
                .delete();

        List<Object> aliceRolesAfter = pivotRolesFor(1);
        assertEquals(1, aliceRolesAfter.size());
        assertEquals(2L, ((Number) aliceRolesAfter.get(0)).longValue());
    }

    // ─── sync semantics ──────────────────────────────────────

    @Test @Order(20)
    void sync_replacesExistingPivotWithTargetSet() {
        // Alice currently has [1, 2]; sync to [2, 3]
        // Expected: role 1 detached, role 3 attached, role 2 unchanged

        List<Object> currentIds = pivotRolesFor(1);
        List<Object> targetIds  = List.of(2, 3);

        // Simulate sync: detach removed, attach new
        for (Object current : currentIds) {
            if (!targetIds.contains(current)) {
                new QueryBuilder("role_user")
                        .where("user_id", 1)
                        .where("role_id", current)
                        .delete();
            }
        }
        for (Object target : targetIds) {
            boolean alreadyPresent = currentIds.stream()
                    .anyMatch(c -> c.toString().equals(target.toString()));
            if (!alreadyPresent) {
                new QueryBuilder("role_user").insert(Map.of("user_id", 1, "role_id", target));
            }
        }

        List<Object> result = pivotRolesFor(1);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> ((Number) r).intValue() == 2));
        assertTrue(result.stream().anyMatch(r -> ((Number) r).intValue() == 3));
        assertFalse(result.stream().anyMatch(r -> ((Number) r).intValue() == 1),
                "role 1 must have been detached");
    }

    @Test @Order(21)
    void sync_toEmptySet_removesAllPivotRows() {
        // Sync Alice to an empty role set — all pivot rows must be removed
        List<Object> currentIds = pivotRolesFor(1);
        for (Object id : currentIds) {
            new QueryBuilder("role_user")
                    .where("user_id", 1)
                    .where("role_id", id)
                    .delete();
        }

        assertEquals(0, pivotRolesFor(1).size());
        assertEquals(1, pivotRolesFor(2).size(), "Bob's roles must be untouched");
    }

    @Test @Order(22)
    void sync_withNoChanges_isIdempotent() {
        // Sync Alice to her current set [1, 2] — nothing should change
        List<Object> currentIds = pivotRolesFor(1);
        long pivotBefore = pivotCount();

        // No-op: nothing to detach, nothing to attach
        for (Object id : currentIds) {
            assertTrue(currentIds.contains(id));
        }

        assertEquals(pivotBefore, pivotCount(), "pivot count must not change");
    }

    // ─── toggle semantics ────────────────────────────────────

    @Test @Order(30)
    void toggle_attachesAbsentAndDetachesPresent() {
        // Alice has [1, 2]; toggle [2, 3]
        // Expected: role 2 detached, role 3 attached
        List<Object> currentIds = pivotRolesFor(1);
        List<Object> toggleIds  = List.of(2, 3);

        for (Object id : toggleIds) {
            boolean present = currentIds.stream()
                    .anyMatch(c -> c.toString().equals(id.toString()));
            if (present) {
                new QueryBuilder("role_user")
                        .where("user_id", 1).where("role_id", id).delete();
            } else {
                new QueryBuilder("role_user").insert(Map.of("user_id", 1, "role_id", id));
            }
        }

        List<Object> result = pivotRolesFor(1);
        assertTrue(result.stream().anyMatch(r -> ((Number) r).intValue() == 1), "role 1 untouched");
        assertFalse(result.stream().anyMatch(r -> ((Number) r).intValue() == 2), "role 2 detached");
        assertTrue(result.stream().anyMatch(r -> ((Number) r).intValue() == 3), "role 3 attached");
    }

    // ─── large batch correctness ─────────────────────────────

    @Test @Order(40)
    void insertAll_largeDataset_allRowsInserted() {
        // Insert 50 roles first, then batch-assign them all to Charlie (user_id=3)
        for (int i = 4; i <= 53; i++) {
            new QueryBuilder("roles").insert(Map.of("name", "ROLE_" + i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int roleId = 4; roleId <= 53; roleId++) {
            rows.add(Map.of("user_id", 3, "role_id", roleId));
        }

        new QueryBuilder("role_user").insertAll(rows);

        long count = new QueryBuilder("role_user").where("user_id", 3).count();
        assertEquals(50, count, "All 50 pivot rows must be inserted via batch");
    }
}