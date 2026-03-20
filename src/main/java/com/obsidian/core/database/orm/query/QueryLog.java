package com.obsidian.core.database.orm.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Query log for debugging and profiling.
 */
public class QueryLog
{
    private static final Logger logger = LoggerFactory.getLogger(QueryLog.class);

    /** Maximum entries retained in memory (~2 MB worst-case at ~200 bytes/entry). */
    public static final int MAX_ENTRIES = 10_000;

    private static volatile boolean enabled = false;

    private static final List<Entry> log = Collections.synchronizedList(new ArrayList<>());

    // ─── CONTROL ─────────────────────────────────────────────

    /** Enables query logging. */
    public static void enable()  { enabled = true; }

    /** Disables query logging. */
    public static void disable() { enabled = false; }

    /** @return true if logging is currently enabled */
    public static boolean isEnabled() { return enabled; }

    // ─── RECORD ──────────────────────────────────────────────

    /**
     * Records a query execution.
     *
     * @param sql        executed SQL string
     * @param bindings   parameter values bound to the query
     * @param durationMs execution time in milliseconds
     */
    public static void record(String sql, List<Object> bindings, long durationMs) {
        if (!enabled) return;
        synchronized (log) {
            if (log.size() >= MAX_ENTRIES) log.remove(0);
            log.add(new Entry(sql, bindings, durationMs));
        }
    }

    // ─── READ ────────────────────────────────────────────────

    /**
     * Returns an immutable snapshot of all logged entries.
     *
     * @return snapshot safe to iterate without synchronisation
     */
    public static List<Entry> getLog() {
        synchronized (log) {
            return Collections.unmodifiableList(new ArrayList<>(log));
        }
    }

    /**
     * Returns the last n entries.
     *
     * @param n number of recent entries to return
     * @return immutable snapshot of at most n entries
     */
    public static List<Entry> last(int n) {
        synchronized (log) {
            int size = log.size();
            if (n >= size) return Collections.unmodifiableList(new ArrayList<>(log));
            return Collections.unmodifiableList(new ArrayList<>(log.subList(size - n, size)));
        }
    }

    /** Clears all recorded entries. */
    public static void clear() { log.clear(); }

    /** @return total number of recorded entries */
    public static int count() { return log.size(); }

    /**
     * Returns the total execution time of all recorded entries.
     *
     * @return total duration in milliseconds
     */
    public static long totalTimeMs() {
        synchronized (log) {
            long total = 0;
            for (Entry e : log) total += e.durationMs;
            return total;
        }
    }

    /** Logs all entries at DEBUG level via SLF4J. */
    public static void dump() {
        List<Entry> snapshot = getLog();
        long total = 0;
        for (Entry e : snapshot) total += e.durationMs;
        logger.debug("=== Query Log ({} queries, {}ms total) ===", snapshot.size(), total);
        for (int i = 0; i < snapshot.size(); i++) {
            Entry e = snapshot.get(i);
            logger.debug("[{}] {}  |  bindings: {}  |  {}ms",  i + 1, e.getSql(), e.getBindings(), e.getDurationMs());
        }
        logger.debug("=== End Query Log ===");
    }

    // ─── ENTRY ───────────────────────────────────────────────

    /** Immutable record of a single query execution. */
    public static class Entry {

        private final String       sql;
        private final List<Object> bindings;
        private final long         durationMs;
        private final long         timestamp;

        /**
         * Creates a log entry.
         *
         * @param sql        executed SQL string
         * @param bindings   parameter values (defensively copied)
         * @param durationMs execution time in milliseconds
         */
        public Entry(String sql, List<Object> bindings, long durationMs) {
            this.sql        = sql;
            this.bindings   = bindings != null ? List.copyOf(bindings) : Collections.emptyList();
            this.durationMs = durationMs;
            this.timestamp  = System.currentTimeMillis();
        }

        /** @return executed SQL string */
        public String       getSql()        { return sql; }
        /** @return bound parameter values */
        public List<Object> getBindings()   { return bindings; }
        /** @return execution time in milliseconds */
        public long         getDurationMs() { return durationMs; }
        /** @return epoch millis when this entry was created */
        public long         getTimestamp()  { return timestamp; }

        /**
         * Returns the SQL with parameter values interpolated.
         *
         * @return interpolated SQL — debug only, never pass to a JDBC driver
         */
        public String toRawSql() {
            String raw = sql;
            for (Object binding : bindings) {
                String replacement;
                if (binding == null)                    replacement = "NULL";
                else if (binding instanceof String)     replacement = "'" + binding.toString().replace("'", "\\'") + "'";
                else                                    replacement = binding.toString();
                raw = raw.replaceFirst("\\?", replacement);
            }
            return raw;
        }

        @Override
        public String toString() { return toRawSql() + " (" + durationMs + "ms)"; }
    }
}