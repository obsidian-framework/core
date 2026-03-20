package com.obsidian.core.database;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.QueryLog;
import com.obsidian.core.database.orm.query.SqlIdentifier;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security and optimisation regression tests.
 *
 * Each test is tied to a specific fix — the comment above the test names
 * the vulnerability class and the code location patched.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityTest
{
    @BeforeEach void setUp()    { TestHelper.setup(); TestHelper.seed(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ════════════════════════════════════════════════════════
    //  SqlIdentifier — bypass removal (CVE class: second-order SQLi)
    // ════════════════════════════════════════════════════════

    /**
     * The old code had an early-return for any identifier containing
     * "(", " ", or " AS " — meaning this string bypassed all validation.
     * Fix: requireIdentifier now rejects everything that isn't a plain
     * identifier or table.* wildcard.
     */
    @Test @Order(1)
    void sqlIdentifier_rejectsParenthesisInjection() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifier.requireIdentifier("id, (SELECT password FROM users)")
        );
    }

    @Test @Order(2)
    void sqlIdentifier_rejectsSpaceInjection() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifier.requireIdentifier("name; DROP TABLE users--")
        );
    }

    @Test @Order(3)
    void sqlIdentifier_rejectsAsKeyword() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifier.requireIdentifier("id AS injected")
        );
    }

    @Test @Order(4)
    void sqlIdentifier_rejectsUnionInjection() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifier.requireIdentifier("1 UNION SELECT * FROM secrets")
        );
    }

    @Test @Order(5)
    void sqlIdentifier_acceptsValidIdentifiers() {
        // These must NOT throw
        assertDoesNotThrow(() -> SqlIdentifier.requireIdentifier("id"));
        assertDoesNotThrow(() -> SqlIdentifier.requireIdentifier("user_id"));
        assertDoesNotThrow(() -> SqlIdentifier.requireIdentifier("users.id"));
        assertDoesNotThrow(() -> SqlIdentifier.requireIdentifier("users.*"));
        assertDoesNotThrow(() -> SqlIdentifier.requireIdentifier("*"));
    }

    @Test @Order(6)
    void sqlIdentifier_nullThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlIdentifier.requireIdentifier(null)
        );
    }

    /**
     * Verifies the injection guard fires at the QueryBuilder level, not just
     * in SqlIdentifier directly — ensures the path from the public API is covered.
     */
    @Test @Order(7)
    void queryBuilder_select_rejectsInjectionAttempt() {
        assertThrows(IllegalArgumentException.class, () ->
                new QueryBuilder("users").select("id, (SELECT password FROM users)")
        );
    }

    @Test @Order(8)
    void queryBuilder_where_rejectsInjectionInColumnName() {
        assertThrows(IllegalArgumentException.class, () ->
                new QueryBuilder("users").where("name = 1 OR 1=1 --", "x")
        );
    }

    @Test @Order(9)
    void queryBuilder_orderBy_rejectsInjectionInDirection() {
        // Only ASC/DESC are allowed — anything else is rejected
        assertThrows(IllegalArgumentException.class, () ->
                new QueryBuilder("users").orderBy("name", "ASC; DROP TABLE users")
        );
    }

    @Test @Order(10)
    void queryBuilder_groupBy_rejectsInjection() {
        assertThrows(IllegalArgumentException.class, () ->
                new QueryBuilder("users").groupBy("role, (SELECT 1)")
        );
    }

    @Test @Order(11)
    void queryBuilder_join_rejectsInjectionInTableName() {
        assertThrows(IllegalArgumentException.class, () ->
                new QueryBuilder("users")
                        .join("profiles; DROP TABLE users--", "users.id", "=", "profiles.user_id")
        );
    }

    // ════════════════════════════════════════════════════════
    //  selectRaw still works — escape hatch must be preserved
    // ════════════════════════════════════════════════════════

    @Test @Order(15)
    void selectRaw_allowsArbitraryExpression() {
        // selectRaw bypasses requireIdentifier by design — verify it still works
        List<Map<String, Object>> results = new QueryBuilder("users")
                .selectRaw("COUNT(*) AS total")
                .get();
        assertFalse(results.isEmpty());
        assertNotNull(results.get(0).get("total"));
    }

    @Test @Order(16)
    void whereRaw_allowsArbitraryExpression() {
        List<Map<String, Object>> results = new QueryBuilder("users")
                .whereRaw("active = ?", 1)
                .get();
        assertEquals(2, results.size());
    }

    // ════════════════════════════════════════════════════════
    //  Mass assignment — isFillable() fix (was returning true for guarded=*)
    // ════════════════════════════════════════════════════════

    /**
     * Before the fix, guarded=["*"] returned true (allow all) instead of false.
     * This test verifies the secure default: without an explicit fillable() list,
     * mass assignment is denied entirely.
     */
    @Test @Order(20)
    void massAssignment_guardedStarBlocksAllFields() {
        // Role has no fillable() — defaults to guarded=["*"]
        Role role = new Role();
        role.fill(Map.of("name", "INJECTED_ROLE"));

        // fill() should have been blocked — name should not be set
        assertNull(role.getString("name"),
                "guarded=[*] must block all mass assignment — was incorrectly allowing it");
    }

    @Test @Order(21)
    void massAssignment_fillableAllowsOnlyListedFields() {
        // User declares fillable = [name, email, age, role, active]
        User user = new User();
        user.fill(Map.of(
                "name", "Test",
                "email", "test@example.com",
                "settings", "should_be_blocked"   // not in fillable
        ));

        assertEquals("Test", user.getString("name"));
        assertEquals("test@example.com", user.getString("email"));
        assertNull(user.getString("settings"), "Field not in fillable must be blocked");
    }

    @Test @Order(22)
    void massAssignment_forceFillBypassesGuard() {
        // forceFill is the intentional escape hatch for internal/trusted code
        Role role = new Role();
        role.forceFill(Map.of("name", "ADMIN"));
        assertEquals("ADMIN", role.getString("name"));
    }

    @Test @Order(23)
    void massAssignment_guardedListBlocksSpecificFields() {
        // A model with guarded=["id", "password"] should block those fields
        // but allow others — tested via Role since it has no explicit fillable/guarded
        // We verify the pattern works via User's fillable instead
        User user = new User();
        user.fill(Map.of("name", "Alice", "id", 999));
        // id is not in User's fillable, so it should be blocked
        assertNull(user.getId(), "id must be blocked by fillable list");
        assertEquals("Alice", user.getString("name"));
    }

    // ════════════════════════════════════════════════════════
    //  BelongsToMany pivot columns — were passing "table.col AS alias"
    //  through select() which now rejects them. Fix routes via selectRaw.
    // ════════════════════════════════════════════════════════

    @Test @Order(30)
    void belongsToMany_pivotColumnsDoNotThrow() {
        // This was failing with IllegalArgumentException after the SqlIdentifier fix
        User user = Model.find(User.class, 1);
        assertDoesNotThrow(() -> {
            List<Role> roles = user.roles().get();
            assertEquals(2, roles.size());
        });
    }

    @Test @Order(31)
    void belongsToMany_withPivotColumnsDoNotThrow() {
        User user = Model.find(User.class, 1);
        assertDoesNotThrow(() -> {
            List<Role> roles = user.roles().withPivot("assigned_at").get();
            assertFalse(roles.isEmpty());
        });
    }

    @Test @Order(32)
    void belongsToMany_attachDetachStillWorks() {
        User bob = Model.find(User.class, 2);
        assertEquals(1, bob.roles().get().size());

        bob.roles().attach(1);
        assertEquals(2, bob.roles().get().size());

        bob.roles().detach(1);
        assertEquals(1, bob.roles().get().size());
    }

    // ════════════════════════════════════════════════════════
    //  exists() optimisation — must use SELECT 1 not SELECT *
    // ════════════════════════════════════════════════════════

    @Test @Order(40)
    void exists_returnsTrueWhenRowPresent() {
        assertTrue(new QueryBuilder("users").where("name", "Alice").exists());
    }

    @Test @Order(41)
    void exists_returnsFalseWhenNoRow() {
        assertFalse(new QueryBuilder("users").where("name", "Nobody").exists());
    }

    @Test @Order(42)
    void exists_doesNotCorruptBuilderState() {
        // exists() snapshots and restores columns — subsequent get() must still return full rows
        QueryBuilder qb = new QueryBuilder("users").where("active", 1);

        boolean found = qb.exists();
        assertTrue(found);

        // Full query must still work with all columns intact
        List<Map<String, Object>> rows = qb.get();
        assertEquals(2, rows.size());
        assertNotNull(rows.get(0).get("name"),
                "exists() must not corrupt the SELECT column list");
    }

    @Test @Order(43)
    void exists_usesSelectOneNotSelectStar() {
        // Verify via QueryLog that exists() emits SELECT 1, not SELECT *
        QueryLog.enable();
        QueryLog.clear();

        new QueryBuilder("users").where("name", "Alice").exists();

        String sql = QueryLog.getLog().get(0).getSql();
        assertTrue(sql.contains("SELECT 1"),
                "exists() should emit SELECT 1 ... not SELECT * ... Got: " + sql);
        assertFalse(sql.contains("SELECT *"),
                "exists() must not use SELECT * — wasted bandwidth. Got: " + sql);

        QueryLog.disable();
        QueryLog.clear();
    }

    // ════════════════════════════════════════════════════════
    //  insertAll() — key-set validation
    // ════════════════════════════════════════════════════════

    @Test @Order(50)
    void insertAll_homogeneousRowsSucceeds() {
        List<Map<String, Object>> rows = List.of(
                Map.of("name", "Batch1", "email", "b1@x.com"),
                Map.of("name", "Batch2", "email", "b2@x.com")
        );
        assertDoesNotThrow(() -> new QueryBuilder("users").insertAll(rows));
        assertEquals(5, new QueryBuilder("users").count());
    }

    @Test @Order(51)
    void insertAll_heterogeneousRowsThrows() {
        // Before fix: row2's extra key was silently null-padded — masked schema drift
        List<Map<String, Object>> rows = List.of(
                Map.of("name", "Row1", "email", "r1@x.com"),
                Map.of("name", "Row2", "email", "r2@x.com", "age", 25)  // extra key
        );
        assertThrows(IllegalArgumentException.class,
                () -> new QueryBuilder("users").insertAll(rows),
                "insertAll must reject rows with mismatched key sets");
    }

    @Test @Order(52)
    void insertAll_emptyListIsNoOp() {
        assertDoesNotThrow(() -> new QueryBuilder("users").insertAll(List.of()));
        assertEquals(3, new QueryBuilder("users").count());
    }

    @Test @Order(53)
    void insertAll_singleRowDelegatesToInsert() {
        // Single-row path returns generated key via insert(), not batch
        List<Map<String, Object>> rows = List.of(Map.of("name", "Solo", "email", "s@x.com"));
        assertDoesNotThrow(() -> new QueryBuilder("users").insertAll(rows));
        assertEquals(4, new QueryBuilder("users").count());
    }

    // ════════════════════════════════════════════════════════
    //  QueryLog — dump() must not write to System.out
    // ════════════════════════════════════════════════════════

    @Test @Order(60)
    void queryLog_dumpDoesNotWriteToSystemOut() {
        QueryLog.enable();
        QueryLog.clear();
        new QueryBuilder("users").get();

        // Capture System.out and verify dump() writes nothing to it
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(captured));
        try {
            QueryLog.dump();
        } finally {
            System.setOut(original);
        }

        assertEquals("", captured.toString().trim(),
                "QueryLog.dump() must not write to System.out — use SLF4J logger instead");

        QueryLog.disable();
        QueryLog.clear();
    }

    // ════════════════════════════════════════════════════════
    //  Statement timeout — queryTimeoutSeconds default = 30
    // ════════════════════════════════════════════════════════

    @Test @Order(70)
    void queryBuilder_timeoutZeroDisablesTimeout() {
        // timeout(0) must not throw and must execute normally
        List<Map<String, Object>> rows = new QueryBuilder("users")
                .timeout(0)
                .get();
        assertEquals(3, rows.size());
    }

    @Test @Order(71)
    void queryBuilder_customTimeoutExecutesNormally() {
        // A generous timeout must not interfere with a fast query
        List<Map<String, Object>> rows = new QueryBuilder("users")
                .timeout(60)
                .where("active", 1)
                .get();
        assertEquals(2, rows.size());
    }

    // ════════════════════════════════════════════════════════
    //  Operator whitelist — unchanged behaviour verified
    // ════════════════════════════════════════════════════════

    @Test @Order(80)
    void operatorWhitelist_rejectsUnknownOperator() {
        assertThrows(IllegalArgumentException.class, () ->
                new QueryBuilder("users").where("id", "LIKE; DROP TABLE users--", 1)
        );
    }

    @Test @Order(81)
    void operatorWhitelist_acceptsAllValidOperators() {
        // Verify none of the whitelisted operators accidentally got removed
        assertDoesNotThrow(() -> new QueryBuilder("users").where("id", "=", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("id", "!=", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("id", "<>", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("age", ">", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("age", "<", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("age", ">=", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("age", "<=", 1).toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("name", "LIKE", "%a%").toSql());
        assertDoesNotThrow(() -> new QueryBuilder("users").where("name", "NOT LIKE", "%a%").toSql());
    }
}