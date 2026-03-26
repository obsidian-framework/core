package com.obsidian.core.database.query;

import com.obsidian.core.database.orm.query.grammar.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for compileUpsert() across all three grammar implementations.
 *
 * Covers:
 *   - Correct SQL syntax per dialect
 *   - Binding order and count
 *   - updateKeys empty → all non-unique columns updated
 *   - updateKeys explicit → only specified columns updated
 *   - Multiple rows
 *   - Empty rows → throws
 *   - Empty uniqueKeys on Postgres/SQLite → throws
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpsertGrammarTest
{
    // ─── Helpers ─────────────────────────────────────────────

    private Map<String, Object> row(Object... kv) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ─── MySqlGrammar ─────────────────────────────────────────

    @Test @Order(1)
    void mysql_upsert_basicSyntax() {
        MySqlGrammar grammar = new MySqlGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice", "age", 30)),
                List.of("email"),
                List.of()
        );

        String sql = result.getSql();
        assertTrue(sql.startsWith("INSERT INTO users"),          "Must start with INSERT INTO users");
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"),      "Must use ON DUPLICATE KEY UPDATE");
        assertTrue(sql.contains("name = VALUES(name)"),          "Must update name");
        assertTrue(sql.contains("age = VALUES(age)"),            "Must update age");
        assertFalse(sql.contains("email = VALUES(email)"),       "Must NOT update unique key column");
    }

    @Test @Order(2)
    void mysql_upsert_explicitUpdateKeys() {
        MySqlGrammar grammar = new MySqlGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice", "age", 30)),
                List.of("email"),
                List.of("name")
        );

        String sql = result.getSql();
        assertTrue(sql.contains("name = VALUES(name)"),    "Must update name");
        assertFalse(sql.contains("age = VALUES(age)"),     "Must NOT update age when not in updateKeys");
        assertFalse(sql.contains("email = VALUES(email)"), "Must NOT update email");
    }

    @Test @Order(3)
    void mysql_upsert_multipleRows_correctBindings() {
        MySqlGrammar grammar = new MySqlGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(
                        row("email", "a@b.com", "name", "Alice"),
                        row("email", "b@b.com", "name", "Bob")
                ),
                List.of("email"),
                List.of()
        );

        String sql = result.getSql();
        // Two value sets: (?, ?), (?, ?)
        long commaCount = sql.chars().filter(c -> c == '(').count();
        assertTrue(commaCount >= 2, "Must have at least 2 value groups");

        // 2 rows × 2 columns = 4 bindings
        assertEquals(4, result.getBindings().size());
        assertEquals("a@b.com", result.getBindings().get(0));
        assertEquals("Alice",   result.getBindings().get(1));
        assertEquals("b@b.com", result.getBindings().get(2));
        assertEquals("Bob",     result.getBindings().get(3));
    }

    @Test @Order(4)
    void mysql_upsert_emptyRows_throws() {
        MySqlGrammar grammar = new MySqlGrammar();
        assertThrows(IllegalArgumentException.class,
                () -> grammar.compileUpsert("users", List.of(), List.of("email"), List.of()));
    }

    @Test @Order(5)
    void mysql_upsert_emptyUniqueKeys_updatesAllColumns() {
        // MySQL: empty uniqueKeys means no column is excluded from UPDATE clause
        MySqlGrammar grammar = new MySqlGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice")),
                List.of(),   // no unique key → all columns go into ON DUPLICATE KEY UPDATE
                List.of()
        );

        String sql = result.getSql();
        assertTrue(sql.contains("email = VALUES(email)"), "email must be in UPDATE clause");
        assertTrue(sql.contains("name = VALUES(name)"),   "name must be in UPDATE clause");
    }

    // ─── PostgresGrammar ──────────────────────────────────────

    @Test @Order(20)
    void postgres_upsert_basicSyntax() {
        PostgresGrammar grammar = new PostgresGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice", "age", 30)),
                List.of("email"),
                List.of()
        );

        String sql = result.getSql();
        assertTrue(sql.contains("ON CONFLICT"),              "Must contain ON CONFLICT");
        assertTrue(sql.contains("DO UPDATE SET"),            "Must contain DO UPDATE SET");
        assertTrue(sql.contains("EXCLUDED."),                "Must reference EXCLUDED pseudo-table");
        assertFalse(sql.contains("email = EXCLUDED"),        "Must NOT update unique key column");
    }

    @Test @Order(21)
    void postgres_upsert_conflictTargetCorrect() {
        PostgresGrammar grammar = new PostgresGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice")),
                List.of("email"),
                List.of()
        );

        String sql = result.getSql();
        // Conflict target must include quoted column
        assertTrue(sql.contains("ON CONFLICT") && sql.contains("email"),
                "Conflict target must mention email");
    }

    @Test @Order(22)
    void postgres_upsert_explicitUpdateKeys() {
        PostgresGrammar grammar = new PostgresGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice", "age", 30)),
                List.of("email"),
                List.of("age")
        );

        String sql = result.getSql();
        assertTrue(sql.contains("EXCLUDED"),             "Must reference EXCLUDED");
        assertTrue(sql.contains("age"),                   "Must update age");
        assertFalse(sql.contains("name = EXCLUDED"),      "Must NOT update name");
    }

    @Test @Order(23)
    void postgres_upsert_emptyRows_throws() {
        PostgresGrammar grammar = new PostgresGrammar();
        assertThrows(IllegalArgumentException.class,
                () -> grammar.compileUpsert("users", List.of(), List.of("email"), List.of()));
    }

    @Test @Order(24)
    void postgres_upsert_emptyUniqueKeys_throws() {
        PostgresGrammar grammar = new PostgresGrammar();
        assertThrows(IllegalArgumentException.class,
                () -> grammar.compileUpsert(
                        "users",
                        List.of(row("email", "a@b.com")),
                        List.of(),   // empty uniqueKeys not allowed on Postgres
                        List.of()
                ));
    }

    @Test @Order(25)
    void postgres_upsert_multipleRows_correctBindingCount() {
        PostgresGrammar grammar = new PostgresGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(
                        row("email", "a@b.com", "name", "Alice"),
                        row("email", "b@b.com", "name", "Bob"),
                        row("email", "c@b.com", "name", "Charlie")
                ),
                List.of("email"),
                List.of()
        );

        // 3 rows × 2 columns = 6 bindings
        assertEquals(6, result.getBindings().size());
    }

    // ─── SQLiteGrammar ────────────────────────────────────────

    @Test @Order(40)
    void sqlite_upsert_basicSyntax() {
        SQLiteGrammar grammar = new SQLiteGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice", "age", 30)),
                List.of("email"),
                List.of()
        );

        String sql = result.getSql();
        assertTrue(sql.startsWith("INSERT INTO users"),  "Must start with INSERT INTO users");
        assertTrue(sql.contains("ON CONFLICT"),          "Must use ON CONFLICT");
        assertTrue(sql.contains("DO UPDATE SET"),        "Must use DO UPDATE SET");
        assertTrue(sql.contains("excluded."),            "Must reference excluded pseudo-table (lowercase)");
        assertFalse(sql.contains("email = excluded"),    "Must NOT update unique key column");
    }

    @Test @Order(41)
    void sqlite_upsert_explicitUpdateKeys() {
        SQLiteGrammar grammar = new SQLiteGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice", "age", 30)),
                List.of("email"),
                List.of("name")
        );

        String sql = result.getSql();
        assertTrue(sql.contains("name = excluded.name"), "Must update name via excluded");
        assertFalse(sql.contains("age = excluded.age"),  "Must NOT update age");
    }

    @Test @Order(42)
    void sqlite_upsert_emptyRows_throws() {
        SQLiteGrammar grammar = new SQLiteGrammar();
        assertThrows(IllegalArgumentException.class,
                () -> grammar.compileUpsert("users", List.of(), List.of("email"), List.of()));
    }

    @Test @Order(43)
    void sqlite_upsert_emptyUniqueKeys_throws() {
        SQLiteGrammar grammar = new SQLiteGrammar();
        assertThrows(IllegalArgumentException.class,
                () -> grammar.compileUpsert(
                        "users",
                        List.of(row("email", "a@b.com")),
                        List.of(),
                        List.of()
                ));
    }

    @Test @Order(44)
    void sqlite_upsert_bindingsInRowOrder() {
        SQLiteGrammar grammar = new SQLiteGrammar();
        InsertResult result = grammar.compileUpsert(
                "users",
                List.of(row("email", "a@b.com", "name", "Alice")),
                List.of("email"),
                List.of()
        );

        assertEquals(2, result.getBindings().size());
        assertEquals("a@b.com", result.getBindings().get(0));
        assertEquals("Alice",   result.getBindings().get(1));
    }

    // ─── Cross-dialect: binding count invariant ───────────────

    @Test @Order(60)
    void allDialects_bindingCount_equalsRowsTimesColumns() {
        List<Map<String, Object>> rows = List.of(
                row("email", "a@b.com", "name", "Alice", "age", 30),
                row("email", "b@b.com", "name", "Bob",   "age", 25)
        );
        List<String> uniqueKeys  = List.of("email");
        List<String> updateKeys  = List.of();

        int expected = 2 * 3; // 2 rows × 3 columns

        assertEquals(expected, new MySqlGrammar()   .compileUpsert("users", rows, uniqueKeys, updateKeys).getBindings().size());
        assertEquals(expected, new PostgresGrammar().compileUpsert("users", rows, uniqueKeys, updateKeys).getBindings().size());
        assertEquals(expected, new SQLiteGrammar()  .compileUpsert("users", rows, uniqueKeys, updateKeys).getBindings().size());
    }
}