package com.obsidian.core.database.orm.query;

import com.obsidian.core.database.DB;
import com.obsidian.core.database.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Handles JDBC execution for QueryBuilder.
 *
 * <p>Owns PreparedStatement lifecycle, parameter binding, ResultSet
 * hydration, batch insert, chunked streaming, and QueryLog recording.</p>
 */
class QueryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

    private final int queryTimeoutSeconds;

    /**
     * Creates an executor with the given query timeout.
     *
     * @param queryTimeoutSeconds timeout applied to every statement, 0 to disable
     */
    QueryExecutor(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    // ─── SELECT ──────────────────────────────────────────────

    /**
     * Executes a SELECT query and returns all rows.
     *
     * @param sql    compiled SQL string
     * @param params bound parameter values
     * @return list of rows as column-to-value maps
     */
    List<Map<String, Object>> executeQuery(String sql, List<Object> params) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            applyTimeout(stmt);
            bindParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> result = resultSetToList(rs);
                QueryLog.record(sql, params, System.currentTimeMillis() - start);
                return result;
            }
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            logger.error("Query failed: {}", sql, e);
            throw new RuntimeException("Database query failed", e);
        }
    }

    // ─── INSERT ──────────────────────────────────────────────

    /**
     * Executes an INSERT and returns the generated key.
     *
     * @param sql    compiled INSERT SQL
     * @param params bound parameter values
     * @return generated key, or null
     */
    Object executeInsert(String sql, List<Object> params) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            applyTimeout(stmt);
            bindParameters(stmt, params);
            stmt.executeUpdate();
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getObject(1);
                return null;
            }
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            logger.error("Insert failed: {}", sql, e);
            throw new RuntimeException("Database insert failed", e);
        }
    }

    /**
     * Executes a batch INSERT for multiple rows in a single roundtrip.
     *
     * @param table        target table name
     * @param sql          compiled INSERT SQL derived from row 0
     * @param batchColumns column names in the order they appear in sql
     * @param rows         all rows to insert
     */
    void executeBatch(String table, String sql, List<String> batchColumns,
                      List<Map<String, Object>> rows) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            applyTimeout(stmt);
            for (Map<String, Object> row : rows) {
                List<Object> rowBindings = new ArrayList<>(batchColumns.size());
                for (String col : batchColumns) rowBindings.add(row.get(col));
                bindParameters(stmt, rowBindings);
                stmt.addBatch();
            }
            stmt.executeBatch();
            QueryLog.record(sql, List.of("[batch x" + rows.size() + "]"),
                    System.currentTimeMillis() - start);
        } catch (SQLException e) {
            QueryLog.record(sql, List.of("[batch x" + rows.size() + "]"),
                    System.currentTimeMillis() - start);
            logger.error("Batch insert failed on table: {}", table, e);
            throw new RuntimeException("Database batch insert failed", e);
        }
    }

    // ─── UPDATE / DELETE ─────────────────────────────────────

    /**
     * Executes an UPDATE, DELETE, or DDL statement.
     *
     * @param sql    compiled SQL string
     * @param params bound parameter values
     * @return number of affected rows
     */
    int executeUpdate(String sql, List<Object> params) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            applyTimeout(stmt);
            bindParameters(stmt, params);
            int result = stmt.executeUpdate();
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            return result;
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            logger.error("Update/Delete failed: {}", sql, e);
            throw new RuntimeException("Database update failed", e);
        }
    }

    // ─── STREAMING ───────────────────────────────────────────

    /**
     * Streams rows without loading the full result set into memory.
     *
     * @param sql       compiled SQL string
     * @param params    bound parameter values
     * @param fetchSize rows per round-trip (Integer.MIN_VALUE for MySQL streaming)
     * @param consumer  called once per row — the map is reused, do not retain references
     */
    void executeChunk(String sql, List<Object> params, int fetchSize,
                      Consumer<Map<String, Object>> consumer) {
        Connection conn = DB.getConnection();

        if (DB.getInstance().getType() == DatabaseType.POSTGRESQL) {
            try {
                if (conn.getAutoCommit()) {
                    throw new IllegalStateException(
                            "chunk() on PostgreSQL requires an active transaction. " +
                                    "Wrap the call with DB.withTransaction(() -> { ... }).");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Could not check autoCommit state", e);
            }
        }

        long start = System.currentTimeMillis();
        try (PreparedStatement stmt = conn.prepareStatement(sql,
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(fetchSize);
            applyTimeout(stmt);
            bindParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                Map<String, Object> row = new LinkedHashMap<>(colCount * 2);
                while (rs.next()) {
                    row.clear();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    consumer.accept(row);
                }
            }
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            logger.error("Chunk query failed: {}", sql, e);
            throw new RuntimeException("Database chunk query failed", e);
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────

    private void applyTimeout(PreparedStatement stmt) throws SQLException {
        if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
    }

    /**
     * Binds parameter values to a PreparedStatement.
     *
     * @param stmt   statement to bind to
     * @param params values in placeholder order
     */
    void bindParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (value instanceof String s)                { stmt.setString(i + 1, s); }
            else if (value instanceof Integer iv)                { stmt.setInt(i + 1, iv); }
            else if (value instanceof Long lv)                   { stmt.setLong(i + 1, lv); }
            else if (value instanceof Double dv)                 { stmt.setDouble(i + 1, dv); }
            else if (value instanceof Float fv)                  { stmt.setFloat(i + 1, fv); }
            else if (value instanceof Boolean bv)                { stmt.setBoolean(i + 1, bv); }
            else if (value instanceof java.util.Date d)          { stmt.setTimestamp(i + 1, new Timestamp(d.getTime())); }
            else if (value instanceof java.time.LocalDateTime ldt){ stmt.setTimestamp(i + 1, Timestamp.valueOf(ldt)); }
            else if (value instanceof java.time.LocalDate ld)    { stmt.setDate(i + 1, java.sql.Date.valueOf(ld)); }
            else                                                  { stmt.setObject(i + 1, value); }
        }
    }

    /**
     * Converts a ResultSet to a list of column-to-value maps.
     *
     * @param rs open ResultSet to read from
     * @return list of rows
     */
    List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>(64);
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(colCount * 2);
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }
}