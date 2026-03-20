package com.obsidian.core.database.orm.query;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query log for debugging.
 *
 * <p><b>Thread safety</b>: backed by a {@code synchronizedList} rather than
 * {@code CopyOnWriteArrayList}. COWAL copies the entire backing array on
 * every {@code add()}, which under sustained load (100+ queries/sec) creates
 * massive GC pressure. {@code synchronizedList} locks briefly on write and
 * we take explicit snapshots on read — cheaper overall because
 * {@link #record} (write) fires on every query while {@link #getLog()} /
 * {@link #last(int)} / {@link #dump()} (read) are only called during debug.</p>
 *
 * Usage:
 *   QueryLog.enable();
 *
 *   // ... run queries ...
 *
 *   QueryLog.getLog().forEach(entry -> {
 *       System.out.println(entry.getSql());
 *       System.out.println(entry.getBindings());
 *       System.out.println(entry.getDurationMs() + "ms");
 *   });
 *
 *   QueryLog.clear();
 *   QueryLog.disable();
 */
public class QueryLog {

    private static final Logger logger = LoggerFactory.getLogger(QueryLog.class);

    private static volatile boolean enabled = false;

    /**
     * Synchronized list — cheap O(1) writes via a brief mutex.
     * Read methods that iterate take a snapshot first (see below).
     */
    private static final List<Entry> log = Collections.synchronizedList(new ArrayList<>());

    /**
     * Enables query logging.
     */
    public static void enable() {
        enabled = true;
    }

    /**
     * Disables query logging.
     */
    public static void disable() {
        enabled = false;
    }

    /**
     * Returns whether query logging is currently enabled.
     *
     * @return {@code true} if enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Records a query execution in the log.
     *
     * <p>No-op when logging is disabled. The {@code volatile} read of
     * {@code enabled} is the only cost on the hot path when disabled.</p>
     *
     * @param sql        the executed SQL string
     * @param bindings   the parameter values bound to the query
     * @param durationMs the query execution time in milliseconds
     */
    public static void record(String sql, List<Object> bindings, long durationMs) {
        if (!enabled) return;
        log.add(new Entry(sql, bindings, durationMs));
    }

    /**
     * Returns a snapshot of all logged entries.
     *
     * <p>The returned list is a detached copy — safe to iterate without
     * synchronisation and unaffected by concurrent {@link #record} calls.</p>
     *
     * @return an unmodifiable snapshot of all entries
     */
    public static List<Entry> getLog() {
        synchronized (log) {
            return Collections.unmodifiableList(new ArrayList<>(log));
        }
    }

    /**
     * Clears all recorded entries.
     */
    public static void clear() {
        log.clear();
    }

    /**
     * Returns the last {@code n} entries (or all if fewer than {@code n} exist).
     *
     * @param n the number of recent entries to return
     * @return an unmodifiable snapshot of the last {@code n} entries
     */
    public static List<Entry> last(int n) {
        synchronized (log) {
            int size = log.size();
            if (n >= size) return Collections.unmodifiableList(new ArrayList<>(log));
            return Collections.unmodifiableList(new ArrayList<>(log.subList(size - n, size)));
        }
    }

    /**
     * Returns the total number of recorded queries.
     *
     * @return entry count
     */
    public static int count() {
        return log.size();
    }

    /**
     * Returns the total execution time of all recorded queries.
     *
     * @return total time in milliseconds
     */
    public static long totalTimeMs() {
        synchronized (log) {
            long total = 0;
            for (Entry e : log) {
                total += e.durationMs;
            }
            return total;
        }
    }

    /**
     * Logs all recorded queries at DEBUG level via SLF4J.
     *
     * <p>Routed through the application logger rather than {@code System.out}
     * so that production log aggregators can capture and suppress it.</p>
     */
    public static void dump() {
        List<Entry> snapshot = getLog();
        long total = 0;
        for (Entry e : snapshot) total += e.durationMs;

        logger.debug("=== Query Log ({} queries, {}ms total) ===", snapshot.size(), total);
        for (int i = 0; i < snapshot.size(); i++) {
            Entry e = snapshot.get(i);
            logger.debug("[{}] {}  |  bindings: {}  |  {}ms",
                    i + 1, e.getSql(), e.getBindings(), e.getDurationMs());
        }
        logger.debug("=== End Query Log ===");
    }

    // ─── Entry ───────────────────────────────────────────────

    public static class Entry {
        private final String sql;
        private final List<Object> bindings;
        private final long durationMs;
        private final long timestamp;

        /**
         * Creates a new log entry.
         *
         * @param sql        the executed SQL string
         * @param bindings   the parameter values (defensively copied)
         * @param durationMs the execution time in milliseconds
         */
        public Entry(String sql, List<Object> bindings, long durationMs) {
            this.sql = sql;
            this.bindings = bindings != null ? List.copyOf(bindings) : Collections.emptyList();
            this.durationMs = durationMs;
            this.timestamp = System.currentTimeMillis();
        }

        /** Returns the executed SQL string. */
        public String getSql()            { return sql; }
        /** Returns the bound parameter values. */
        public List<Object> getBindings() { return bindings; }
        /** Returns the execution time in milliseconds. */
        public long getDurationMs()       { return durationMs; }
        /** Returns the epoch millis at which this entry was created. */
        public long getTimestamp()         { return timestamp; }

        /**
         * Returns the SQL with parameter values interpolated (debug only — not safe for execution).
         *
         * @return the interpolated SQL string
         */
        public String toRawSql() {
            String raw = sql;
            for (Object binding : bindings) {
                String replacement;
                if (binding == null) {
                    replacement = "NULL";
                } else if (binding instanceof String) {
                    replacement = "'" + binding.toString().replace("'", "\\'") + "'";
                } else {
                    replacement = binding.toString();
                }
                raw = raw.replaceFirst("\\?", replacement);
            }
            return raw;
        }

        @Override
        public String toString() {
            return toRawSql() + " (" + durationMs + "ms)";
        }
    }
}