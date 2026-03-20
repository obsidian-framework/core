package com.obsidian.core.database;

import com.obsidian.core.database.orm.query.QueryLog;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryLog capacity limiting.
 *
 * Covers the fix from the audit: unbounded memory growth when enable() is left
 * on in production. The log now caps at MAX_ENTRIES and evicts the oldest entry
 * (FIFO) when the limit is reached.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryLogCapacityTest {

    @BeforeEach void setUp()    { QueryLog.enable(); QueryLog.clear(); }
    @AfterEach  void tearDown() { QueryLog.disable(); QueryLog.clear(); }

    // ─── CAP ENFORCEMENT ─────────────────────────────────────

    @Test @Order(1)
    void log_neverExceedsMaxEntries() {
        int over = QueryLog.MAX_ENTRIES + 500;
        for (int i = 0; i < over; i++) {
            QueryLog.record("SELECT " + i, List.of(), 1);
        }

        assertEquals(QueryLog.MAX_ENTRIES, QueryLog.count(),
                "Log must be capped at MAX_ENTRIES");
    }

    @Test @Order(2)
    void log_evictsOldestEntryOnOverflow() {
        // Fill to capacity
        for (int i = 0; i < QueryLog.MAX_ENTRIES; i++) {
            QueryLog.record("SELECT " + i, List.of(), 1);
        }

        // The oldest entry is "SELECT 0"
        String oldest = QueryLog.getLog().get(0).getSql();
        assertEquals("SELECT 0", oldest);

        // Add one more — "SELECT 0" must be evicted
        QueryLog.record("SELECT NEWEST", List.of(), 1);

        List<QueryLog.Entry> entries = QueryLog.getLog();
        assertEquals(QueryLog.MAX_ENTRIES, entries.size());
        assertEquals("SELECT 1", entries.get(0).getSql(),
                "After eviction, oldest entry must be SELECT 1");
        assertEquals("SELECT NEWEST", entries.get(entries.size() - 1).getSql(),
                "Newest entry must be at the end");
    }

    @Test @Order(3)
    void log_fifo_orderPreservedUnderCap() {
        QueryLog.record("FIRST",  List.of(), 1);
        QueryLog.record("SECOND", List.of(), 2);
        QueryLog.record("THIRD",  List.of(), 3);

        List<QueryLog.Entry> entries = QueryLog.getLog();
        assertEquals("FIRST",  entries.get(0).getSql());
        assertEquals("SECOND", entries.get(1).getSql());
        assertEquals("THIRD",  entries.get(2).getSql());
    }

    @Test @Order(4)
    void log_afterClear_acceptsNewEntriesUpToCap() {
        for (int i = 0; i < QueryLog.MAX_ENTRIES; i++) {
            QueryLog.record("SELECT " + i, List.of(), 1);
        }

        QueryLog.clear();
        assertEquals(0, QueryLog.count());

        QueryLog.record("SELECT AFTER_CLEAR", List.of(), 1);
        assertEquals(1, QueryLog.count());
        assertEquals("SELECT AFTER_CLEAR", QueryLog.getLog().get(0).getSql());
    }

    // ─── DISABLED STATE ──────────────────────────────────────

    @Test @Order(10)
    void disabled_recordIgnored_countStaysZero() {
        QueryLog.disable();
        for (int i = 0; i < 100; i++) {
            QueryLog.record("SELECT " + i, List.of(), 1);
        }
        assertEquals(0, QueryLog.count());
    }

    // ─── last() AFTER OVERFLOW ───────────────────────────────

    @Test @Order(20)
    void last_afterOverflow_returnsCorrectTail() {
        for (int i = 0; i < QueryLog.MAX_ENTRIES + 100; i++) {
            QueryLog.record("SELECT " + i, List.of(), 1);
        }

        List<QueryLog.Entry> tail = QueryLog.last(3);
        assertEquals(3, tail.size());

        // The last 3 entries should be the 3 highest numbered SELECTs
        int total = QueryLog.MAX_ENTRIES + 100;
        assertEquals("SELECT " + (total - 3), tail.get(0).getSql());
        assertEquals("SELECT " + (total - 2), tail.get(1).getSql());
        assertEquals("SELECT " + (total - 1), tail.get(2).getSql());
    }

    // ─── THREAD SAFETY ───────────────────────────────────────

    @Test @Order(30)
    void concurrent_writes_neverExceedCap() throws InterruptedException {
        int threads    = 20;
        int perThread  = QueryLog.MAX_ENTRIES / 5; // intentionally overflow together

        List<Thread> workers = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int tid = t;
            workers.add(new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    QueryLog.record("SELECT t" + tid + "_" + i, List.of(), 1);
                }
            }));
        }

        workers.forEach(Thread::start);
        for (Thread w : workers) w.join(5000);

        assertTrue(QueryLog.count() <= QueryLog.MAX_ENTRIES,
                "Log must never exceed MAX_ENTRIES under concurrent writes");
    }

    @Test @Order(31)
    void concurrent_readWhileWriting_doesNotThrow() throws InterruptedException {
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 5000; i++) {
                QueryLog.record("SELECT " + i, List.of(), 1);
            }
        });
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                List<QueryLog.Entry> snap = QueryLog.getLog();
                assertNotNull(snap);
                long total = QueryLog.totalTimeMs();
                assertTrue(total >= 0);
            }
        });

        writer.start();
        reader.start();
        writer.join(5000);
        reader.join(5000);
        // No exception = pass
    }
}