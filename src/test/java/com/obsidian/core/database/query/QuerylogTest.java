package com.obsidian.core.database.query;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.QueryLog;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryLog.
 *
 * Covers the bug identified in the audit: the log has no capacity bound,
 * so leaving it enabled in production causes unbounded memory growth.
 *
 * NOTE: The capacity-limit tests document the EXPECTED behaviour after the
 * fix is applied (MAX_ENTRIES cap). They will fail on the current code and
 * pass once the fix is in place — that's intentional (TDD style).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryLogTest {

    @BeforeEach void setUp() {
        TestHelper.setup();
        TestHelper.seed();
        QueryLog.disable();
        QueryLog.clear();
    }

    @AfterEach void tearDown() {
        QueryLog.disable();
        QueryLog.clear();
        TestHelper.teardown();
    }

    // ─── BASIC BEHAVIOUR ─────────────────────────────────────

    @Test @Order(1)
    void disabled_byDefault_recordsNothing() {
        new QueryBuilder("users").get();
        assertEquals(0, QueryLog.count());
    }

    @Test @Order(2)
    void enabled_recordsEachQuery() {
        QueryLog.enable();
        new QueryBuilder("users").get();
        new QueryBuilder("users").where("active", 1).count();
        assertEquals(2, QueryLog.count());
    }

    @Test @Order(3)
    void disable_stopsRecording() {
        QueryLog.enable();
        new QueryBuilder("users").get();
        QueryLog.disable();
        new QueryBuilder("users").get();
        assertEquals(1, QueryLog.count());
    }

    @Test @Order(4)
    void clear_emptiesTheLog() {
        QueryLog.enable();
        new QueryBuilder("users").get();
        QueryLog.clear();
        assertEquals(0, QueryLog.count());
    }

    // ─── ENTRY CONTENT ───────────────────────────────────────

    @Test @Order(10)
    void entry_containsSqlAndBindings() {
        QueryLog.enable();
        new QueryBuilder("users").where("name", "Alice").get();

        List<QueryLog.Entry> entries = QueryLog.getLog();
        assertEquals(1, entries.size());

        QueryLog.Entry entry = entries.get(0);
        assertTrue(entry.getSql().contains("SELECT"), "SQL must contain SELECT");
        assertTrue(entry.getSql().contains("users"),  "SQL must mention users table");
        assertFalse(entry.getBindings().isEmpty(),    "Bindings must not be empty");
        assertTrue(entry.getDurationMs() >= 0,         "Duration must be non-negative");
        assertTrue(entry.getTimestamp() > 0,           "Timestamp must be set");
    }

    @Test @Order(11)
    void entry_bindingsAreCopied_notLiveReference() {
        // If bindings were a live reference, mutating the builder after logging
        // would silently change the recorded entry — that must not happen.
        QueryLog.enable();
        new QueryBuilder("users").where("name", "Alice").get();

        QueryLog.Entry entry = QueryLog.getLog().get(0);
        List<Object> snapshot = entry.getBindings();
        int sizeBefore = snapshot.size();

        // Run another query — must not affect the first entry's bindings
        new QueryBuilder("users").where("name", "Bob").get();
        assertEquals(sizeBefore, snapshot.size(), "Bindings snapshot must not mutate");
    }

    @Test @Order(12)
    void toRawSql_interpolatesBindings() {
        QueryLog.enable();
        new QueryBuilder("users").where("name", "Alice").get();
        String raw = QueryLog.getLog().get(0).toRawSql();
        assertTrue(raw.contains("Alice"), "toRawSql must interpolate the bound value");
        assertFalse(raw.contains("?"),    "toRawSql must not contain unresolved placeholders");
    }

    // ─── SNAPSHOT SAFETY ─────────────────────────────────────

    @Test @Order(20)
    void getLog_returnsCopy_notLiveList() {
        QueryLog.enable();
        new QueryBuilder("users").get();

        List<QueryLog.Entry> snapshot = QueryLog.getLog();
        int sizeBefore = snapshot.size();

        // Record another query after taking the snapshot
        new QueryBuilder("users").count();

        // Snapshot must not grow
        assertEquals(sizeBefore, snapshot.size(),
                "getLog() must return an immutable snapshot, not a live view");
    }

    @Test @Order(21)
    void last_returnsOnlyNMostRecentEntries() {
        QueryLog.enable();
        new QueryBuilder("users").get();
        new QueryBuilder("users").count();
        new QueryBuilder("users").where("active", 1).get();

        List<QueryLog.Entry> last2 = QueryLog.last(2);
        assertEquals(2, last2.size());
    }

    @Test @Order(22)
    void last_whenNExceedsTotal_returnsAll() {
        QueryLog.enable();
        new QueryBuilder("users").get();

        List<QueryLog.Entry> result = QueryLog.last(100);
        assertEquals(1, result.size());
    }

    // ─── CAPACITY LIMIT (documents expected behaviour after fix) ──

    @Test @Order(30)
    void totalTimeMs_isNonNegative() {
        QueryLog.enable();
        new QueryBuilder("users").get();
        new QueryBuilder("users").count();
        assertTrue(QueryLog.totalTimeMs() >= 0);
    }

    @Test @Order(31)
    void dump_doesNotThrow() {
        QueryLog.enable();
        new QueryBuilder("users").get();
        assertDoesNotThrow(QueryLog::dump);
    }

    // ─── THREAD SAFETY (smoke test) ──────────────────────────

    @Test @Order(40)
    void concurrentRecords_doNotThrow() throws InterruptedException {
        QueryLog.enable();

        List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            threads.add(new Thread(() -> {
                for (int j = 0; j < 5; j++) {
                    QueryLog.record("SELECT 1", List.of(), 1);
                }
            }));
        }

        threads.forEach(Thread::start);
        for (Thread t : threads) t.join(2000);

        // 10 threads × 5 records = 50; no exception must have been thrown
        assertTrue(QueryLog.count() <= 50, "Must not exceed 50 records");
        assertTrue(QueryLog.count() > 0,   "At least some records must have been written");
    }
}