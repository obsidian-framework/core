package com.obsidian.core.database.orm.query;

import com.obsidian.core.database.DB;
import com.obsidian.core.database.DatabaseType;
import com.obsidian.core.database.orm.query.clause.*;
import com.obsidian.core.database.orm.query.grammar.*;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Fluent query builder for constructing and executing SQL queries.
 *
 * <p><b>Security model</b>: all values are bound via {@link PreparedStatement}
 * placeholders — never interpolated into SQL. Column names, table names, and
 * operators are validated by {@link SqlIdentifier} before being used in SQL
 * assembly, preventing second-order injection through identifier arguments.
 * Raw methods ({@code selectRaw}, {@code whereRaw}, {@code havingRaw}) bypass
 * identifier validation by design; callers <em>must</em> ensure those strings
 * are not derived from untrusted user input.</p>
 *
 * Usage:
 *   new QueryBuilder("users")
 *       .select("id", "name", "email")
 *       .where("active", "=", 1)
 *       .where("age", ">", 18)
 *       .orderBy("name", "ASC")
 *       .limit(10)
 *       .get();
 */
public class QueryBuilder
{
    private final String table;
    private final Grammar grammar;

    // SELECT
    private final List<String> columns = new ArrayList<>();
    private boolean isDistinct = false;

    // WHERE
    private final List<WhereClause> wheres = new ArrayList<>();

    // JOIN
    private final List<JoinClause> joins = new ArrayList<>();

    // ORDER BY
    private final List<OrderClause> orders = new ArrayList<>();

    // GROUP BY / HAVING
    private final List<String> groups = new ArrayList<>();
    private final List<HavingClause> havings = new ArrayList<>();

    // LIMIT / OFFSET
    private Integer limitValue = null;
    private Integer offsetValue = null;

    // Bindings (ordered)
    private final List<Object> bindings = new ArrayList<>();

    // Eager loads (used by Model layer)
    private final List<String> eagerLoads = new ArrayList<>();

    // Scopes applied
    private final List<Consumer<QueryBuilder>> scopes = new ArrayList<>();

    /**
     * Statement-level query timeout in seconds. Applied via
     * {@link PreparedStatement#setQueryTimeout(int)} on every execution.
     * Default: 30 s — matches HikariCP connectionTimeout so a runaway
     * query fails before the pool is exhausted. Set to 0 to disable.
     */
    private int queryTimeoutSeconds = 30;

    /**
     * Creates a new QueryBuilder instance.
     *
     * @param table The table name
     */
    public QueryBuilder(String table) {
        this(table, GrammarFactory.get());
    }

    /**
     * Creates a new QueryBuilder instance.
     *
     * @param table   The table name
     * @param grammar The grammar
     */
    public QueryBuilder(String table, Grammar grammar) {
        // Skip identifier check for internal sentinel values used by raw()
        if (!table.startsWith("__")) {
            SqlIdentifier.requireIdentifier(table);
        }
        this.table = table;
        this.grammar = grammar;
    }

    // ─── SELECT ──────────────────────────────────────────────

    /**
     * Specifies which columns to retrieve.
     *
     * @param cols Column names — must be valid SQL identifiers
     * @return This builder instance for method chaining
     */
    public QueryBuilder select(String... cols) {
        for (String col : cols) {
            SqlIdentifier.requireIdentifier(col);
        }
        columns.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds a raw expression to the SELECT clause.
     * The caller is responsible for ensuring {@code expression} is safe.
     *
     * @param expression A raw SQL expression (trusted caller input only)
     * @return This builder instance for method chaining
     */
    public QueryBuilder selectRaw(String expression) {
        columns.add(new RawExpression(expression).toString());
        return this;
    }

    /**
     * Adds DISTINCT to the SELECT clause.
     *
     * @return This builder instance for method chaining
     */
    public QueryBuilder distinct() {
        this.isDistinct = true;
        return this;
    }

    // ─── WHERE ───────────────────────────────────────────────

    /**
     * Adds a WHERE condition to the query.
     *
     * @param column   The column name — must be a valid SQL identifier
     * @param operator The comparison operator — must be in the allowed whitelist
     * @param value    The value to compare against (bound via PreparedStatement)
     * @return This builder instance for method chaining
     */
    public QueryBuilder where(String column, String operator, Object value) {
        SqlIdentifier.requireIdentifier(column);
        SqlIdentifier.requireOperator(operator);
        wheres.add(new WhereClause(column, operator, value, "AND"));
        bindings.add(value);
        return this;
    }

    /**
     * Adds a WHERE column = value condition.
     *
     * @param column The column name
     * @param value  The value to compare against
     * @return This builder instance for method chaining
     */
    public QueryBuilder where(String column, Object value) {
        return where(column, "=", value);
    }

    /**
     * Adds an OR WHERE condition to the query.
     *
     * @param column   The column name
     * @param operator The comparison operator
     * @param value    The value to compare against
     * @return This builder instance for method chaining
     */
    public QueryBuilder orWhere(String column, String operator, Object value) {
        SqlIdentifier.requireIdentifier(column);
        SqlIdentifier.requireOperator(operator);
        wheres.add(new WhereClause(column, operator, value, "OR"));
        bindings.add(value);
        return this;
    }

    /**
     * Adds an OR WHERE column = value condition.
     *
     * @param column The column name
     * @param value  The value to compare against
     * @return This builder instance for method chaining
     */
    public QueryBuilder orWhere(String column, Object value) {
        return orWhere(column, "=", value);
    }

    /**
     * Adds a WHERE column IS NULL condition.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereNull(String column) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.isNull(column, "AND"));
        return this;
    }

    /**
     * Adds a WHERE column IS NOT NULL condition.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereNotNull(String column) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.isNotNull(column, "AND"));
        return this;
    }

    /**
     * Removes a previously added WHERE column IS NULL clause.
     * Used internally by withTrashed() to undo the soft delete scope.
     *
     * @param column The column name to remove the NULL check for
     * @return This builder instance for method chaining
     */
    public QueryBuilder removeWhereNull(String column) {
        wheres.removeIf(w ->
                w.getType() == WhereClause.Type.NULL && column.equals(w.getColumn())
        );
        return this;
    }

    /**
     * Adds a WHERE column IN (...) condition.
     *
     * @param column The column name
     * @param values The list of values (bound via PreparedStatement)
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereIn(String column, List<?> values) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.in(column, values, "AND"));
        bindings.addAll(values);
        return this;
    }

    /**
     * Adds a WHERE column NOT IN (...) condition.
     *
     * @param column The column name
     * @param values The list of values
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereNotIn(String column, List<?> values) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.notIn(column, values, "AND"));
        bindings.addAll(values);
        return this;
    }

    /**
     * Adds a WHERE column BETWEEN low AND high condition.
     *
     * @param column The column name
     * @param low    The lower bound of the range
     * @param high   The upper bound of the range
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereBetween(String column, Object low, Object high) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.between(column, low, high, "AND"));
        bindings.add(low);
        bindings.add(high);
        return this;
    }

    /**
     * Adds a WHERE column LIKE pattern condition.
     *
     * @param column  The column name
     * @param pattern The LIKE pattern (e.g. "%john%") — bound as a value, not interpolated
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereLike(String column, String pattern) {
        return where(column, "LIKE", pattern);
    }

    /**
     * Adds a raw WHERE clause (not escaped).
     * The caller is responsible for ensuring {@code sql} is safe.
     *
     * @param sql    Raw SQL string (trusted caller input only)
     * @param params Parameter values to bind to SQL placeholders
     * @return This builder instance for method chaining
     */
    public QueryBuilder whereRaw(String sql, Object... params) {
        wheres.add(WhereClause.raw(sql, "AND"));
        bindings.addAll(Arrays.asList(params));
        return this;
    }

    /**
     * Nested where group:
     *   .where(q -> q.where("a", 1).orWhere("b", 2))
     * Produces: AND (a = ? OR b = ?)
     */
    public QueryBuilder where(Consumer<QueryBuilder> group) {
        QueryBuilder nested = new QueryBuilder(this.table, this.grammar);
        group.accept(nested);
        wheres.add(WhereClause.nested(nested, "AND"));
        bindings.addAll(nested.getBindings());
        return this;
    }

    /**
     * Adds an OR nested WHERE group.
     *
     * @param group A callback that receives a nested QueryBuilder for grouping conditions
     * @return This builder instance for method chaining
     */
    public QueryBuilder orWhere(Consumer<QueryBuilder> group) {
        QueryBuilder nested = new QueryBuilder(this.table, this.grammar);
        group.accept(nested);
        wheres.add(WhereClause.nested(nested, "OR"));
        bindings.addAll(nested.getBindings());
        return this;
    }

    // ─── JOIN ────────────────────────────────────────────────

    /**
     * Adds an INNER JOIN clause to the query.
     *
     * @param table    The table name — must be a valid SQL identifier
     * @param first    The first column — must be a valid SQL identifier
     * @param operator The join operator — must be in the allowed whitelist
     * @param second   The second column — must be a valid SQL identifier
     * @return This builder instance for method chaining
     */
    public QueryBuilder join(String table, String first, String operator, String second) {
        SqlIdentifier.requireIdentifier(table);
        SqlIdentifier.requireIdentifier(first);
        SqlIdentifier.requireOperator(operator);
        SqlIdentifier.requireIdentifier(second);
        joins.add(new JoinClause("INNER", table, first, operator, second));
        return this;
    }

    /**
     * Adds a LEFT JOIN clause to the query.
     *
     * @param table    The table name
     * @param first    The first column
     * @param operator The join operator
     * @param second   The second column
     * @return This builder instance for method chaining
     */
    public QueryBuilder leftJoin(String table, String first, String operator, String second) {
        SqlIdentifier.requireIdentifier(table);
        SqlIdentifier.requireIdentifier(first);
        SqlIdentifier.requireOperator(operator);
        SqlIdentifier.requireIdentifier(second);
        joins.add(new JoinClause("LEFT", table, first, operator, second));
        return this;
    }

    /**
     * Adds a RIGHT JOIN clause to the query.
     *
     * @param table    The table name
     * @param first    The first column
     * @param operator The join operator
     * @param second   The second column
     * @return This builder instance for method chaining
     */
    public QueryBuilder rightJoin(String table, String first, String operator, String second) {
        SqlIdentifier.requireIdentifier(table);
        SqlIdentifier.requireIdentifier(first);
        SqlIdentifier.requireOperator(operator);
        SqlIdentifier.requireIdentifier(second);
        joins.add(new JoinClause("RIGHT", table, first, operator, second));
        return this;
    }

    /**
     * Adds a CROSS JOIN clause to the query.
     *
     * @param table The table name
     * @return This builder instance for method chaining
     */
    public QueryBuilder crossJoin(String table) {
        SqlIdentifier.requireIdentifier(table);
        joins.add(new JoinClause("CROSS", table, null, null, null));
        return this;
    }

    // ─── ORDER BY ────────────────────────────────────────────

    /**
     * Adds an ORDER BY clause to the query.
     *
     * @param column    The column name — must be a valid SQL identifier
     * @param direction The sort direction — must be "ASC" or "DESC"
     * @return This builder instance for method chaining
     */
    public QueryBuilder orderBy(String column, String direction) {
        SqlIdentifier.requireIdentifier(column);
        SqlIdentifier.requireDirection(direction);
        orders.add(new OrderClause(column, direction.toUpperCase()));
        return this;
    }

    /**
     * Adds an ORDER BY ASC clause.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public QueryBuilder orderBy(String column) {
        return orderBy(column, "ASC");
    }

    /**
     * Adds an ORDER BY DESC clause.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public QueryBuilder orderByDesc(String column) {
        return orderBy(column, "DESC");
    }

    /**
     * Orders by the given column descending.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public QueryBuilder latest(String column) {
        return orderByDesc(column);
    }

    /**
     * Orders by created_at descending.
     *
     * @return This builder instance for method chaining
     */
    public QueryBuilder latest() {
        return latest("created_at");
    }

    /**
     * Orders by the given column ascending.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public QueryBuilder oldest(String column) {
        return orderBy(column, "ASC");
    }

    /**
     * Orders by created_at ascending.
     *
     * @return This builder instance for method chaining
     */
    public QueryBuilder oldest() {
        return oldest("created_at");
    }

    // ─── GROUP BY / HAVING ───────────────────────────────────

    /**
     * Adds a GROUP BY clause to the query.
     *
     * @param cols Column names — must be valid SQL identifiers
     * @return This builder instance for method chaining
     */
    public QueryBuilder groupBy(String... cols) {
        for (String col : cols) {
            SqlIdentifier.requireIdentifier(col);
        }
        groups.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds a HAVING clause to the query.
     *
     * @param column   The column name — must be a valid SQL identifier
     * @param operator The comparison operator — must be in the allowed whitelist
     * @param value    The value to compare against (bound via PreparedStatement)
     * @return This builder instance for method chaining
     */
    public QueryBuilder having(String column, String operator, Object value) {
        SqlIdentifier.requireIdentifier(column);
        SqlIdentifier.requireOperator(operator);
        havings.add(new HavingClause(column, operator, value));
        bindings.add(value);
        return this;
    }

    /**
     * Adds a raw HAVING clause.
     * The caller is responsible for ensuring {@code sql} is safe.
     *
     * @param sql    Raw SQL string (trusted caller input only)
     * @param params Parameter values to bind to SQL placeholders
     * @return This builder instance for method chaining
     */
    public QueryBuilder havingRaw(String sql, Object... params) {
        havings.add(HavingClause.raw(sql));
        bindings.addAll(Arrays.asList(params));
        return this;
    }

    // ─── LIMIT / OFFSET ─────────────────────────────────────

    /**
     * Sets the maximum number of rows to return.
     *
     * @param limit Maximum number of rows
     * @return This builder instance for method chaining
     */
    public QueryBuilder limit(int limit) {
        this.limitValue = limit;
        return this;
    }

    /**
     * Sets the number of rows to skip.
     *
     * @param offset Number of rows to skip
     * @return This builder instance for method chaining
     */
    public QueryBuilder offset(int offset) {
        this.offsetValue = offset;
        return this;
    }

    /**
     * Alias for limit().
     *
     * @param n The number of items
     * @return This builder instance for method chaining
     */
    public QueryBuilder take(int n) {
        return limit(n);
    }

    /**
     * Alias for offset().
     *
     * @param n The number of items
     * @return This builder instance for method chaining
     */
    public QueryBuilder skip(int n) {
        return offset(n);
    }

    /**
     * Overrides the default statement-level query timeout.
     *
     * <p>Applied via {@link PreparedStatement#setQueryTimeout(int)} on every
     * execution. Use {@code 0} to disable the timeout (not recommended in production).
     * The default of 30 s is intentionally conservative — long-running analytical
     * queries should opt out explicitly rather than silently blocking threads.</p>
     *
     * @param seconds timeout in seconds (0 = no timeout)
     * @return This builder instance for method chaining
     */
    public QueryBuilder timeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
        return this;
    }

    /**
     * Paginate: page starts at 1.
     */
    public QueryBuilder forPage(int page, int perPage) {
        return limit(perPage).offset((page - 1) * perPage);
    }

    // ─── EAGER LOADING (used by Model layer) ─────────────────

    /**
     * Specifies relations to eager-load with the query results.
     *
     * @param relations Names of relations to eager-load
     * @return This builder instance for method chaining
     */
    public QueryBuilder with(String... relations) {
        eagerLoads.addAll(Arrays.asList(relations));
        return this;
    }

    /**
     * Returns the eager loads.
     *
     * @return The eager loads
     */
    public List<String> getEagerLoads() {
        return Collections.unmodifiableList(eagerLoads);
    }

    // ─── SCOPES ──────────────────────────────────────────────

    /**
     * Applies a query scope to the builder.
     *
     * @param scope A query scope function to apply
     * @return This builder instance for method chaining
     */
    public QueryBuilder applyScope(Consumer<QueryBuilder> scope) {
        scope.accept(this);
        return this;
    }

    // ─── EXECUTION: SELECT ───────────────────────────────────

    /**
     * Execute query and return list of maps.
     */
    public List<Map<String, Object>> get() {
        String sql = toSql();
        List<Object> params = getBindings();
        return executeQuery(sql, params);
    }

    /**
     * Get first result or null.
     */
    public Map<String, Object> first() {
        limit(1);
        List<Map<String, Object>> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Find by primary key.
     */
    public Map<String, Object> find(Object id) {
        return where("id", id).first();
    }

    /**
     * Get a single column's values as a list.
     *
     * @param column The column name — must be a valid SQL identifier
     */
    public List<Object> pluck(String column) {
        SqlIdentifier.requireIdentifier(column);
        columns.clear();
        columns.add(column);
        return get().stream()
                .map(row -> row.get(column))
                .collect(Collectors.toList());
    }

    /**
     * Check if any rows exist.
     *
     * <p>Uses {@code SELECT 1 … LIMIT 1} internally — avoids deserialising full
     * rows just to test existence. This is the pattern that consistently performs
     * better than {@code COUNT(*)} or {@code SELECT *} for existence checks.</p>
     */
    public boolean exists() {
        // Snapshot and restore columns so the builder remains usable after the call.
        List<String> savedColumns = new ArrayList<>(this.columns);
        Integer savedLimit = this.limitValue;
        this.columns.clear();
        this.columns.add("1");
        this.limitValue = 1;
        try {
            return !get().isEmpty();
        } finally {
            this.columns.clear();
            this.columns.addAll(savedColumns);
            this.limitValue = savedLimit;
        }
    }

    /**
     * Checks if no rows match the query.
     *
     * @return {@code true} if no rows match
     */
    public boolean doesntExist() {
        return !exists();
    }

    // ─── AGGREGATES ──────────────────────────────────────────

    /**
     * Returns the number of matching rows.
     *
     * @return The count
     */
    public long count() {
        return aggregate("COUNT", "*");
    }

    /**
     * Returns the count of non-null values in the given column.
     *
     * @param column The column name — must be a valid SQL identifier
     * @return The count
     */
    public long count(String column) {
        SqlIdentifier.requireIdentifier(column);
        return aggregate("COUNT", column);
    }

    /**
     * Returns the maximum value of a column.
     *
     * @param column The column name — must be a valid SQL identifier
     * @return The result value, or {@code null} if not found
     */
    public Object max(String column) {
        SqlIdentifier.requireIdentifier(column);
        return aggregateValue("MAX", column);
    }

    /**
     * Returns the minimum value of a column.
     *
     * @param column The column name — must be a valid SQL identifier
     * @return The result value, or {@code null} if not found
     */
    public Object min(String column) {
        SqlIdentifier.requireIdentifier(column);
        return aggregateValue("MIN", column);
    }

    /**
     * Returns the sum of a column.
     *
     * @param column The column name — must be a valid SQL identifier
     * @return The result value, or {@code null} if not found
     */
    public Object sum(String column) {
        SqlIdentifier.requireIdentifier(column);
        return aggregateValue("SUM", column);
    }

    /**
     * Returns the average of a column.
     *
     * @param column The column name — must be a valid SQL identifier
     * @return The result value, or {@code null} if not found
     */
    public Object avg(String column) {
        SqlIdentifier.requireIdentifier(column);
        return aggregateValue("AVG", column);
    }

    private long aggregate(String function, String column) {
        Object val = aggregateValue(function, column);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0;
    }

    /**
     * Executes an aggregate function without mutating the builder state.
     * Preserves WHERE and JOIN clauses but drops ORDER BY, LIMIT, OFFSET, and
     * the original SELECT columns — none of which affect aggregate results.
     *
     * <p>The column argument is validated by each public aggregate method before
     * reaching here; {@code *} is explicitly allowed for {@code COUNT(*)}.</p>
     */
    private Object aggregateValue(String function, String column) {
        String alias = function.toLowerCase() + "_result";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(function).append("(").append(column).append(") AS ").append(alias);
        sql.append(" FROM ").append(table);

        for (JoinClause join : joins) {
            sql.append(" ").append(join.toSql());
        }

        String whereClause = grammar.compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        if (!groups.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groups));
        }

        List<Map<String, Object>> rows = executeQuery(sql.toString(), new ArrayList<>(bindings));
        if (rows.isEmpty()) return null;
        return rows.get(0).get(alias);
    }

    // ─── EXECUTION: INSERT ───────────────────────────────────

    /**
     * Insert a single row and return the generated key.
     */
    public Object insert(Map<String, Object> values) {
        InsertResult result = grammar.compileInsert(table, values);
        return executeInsert(result.getSql(), result.getBindings());
    }

    /**
     * Insert multiple rows using a single JDBC batch — one roundtrip regardless of row count.
     *
     * <p>All rows must share the same set of columns (the keys of the first row).
     * Rows with a different key set will have missing columns bound as {@code null}.
     * Generated keys are not returned; use {@link #insert(Map)} if you need the key.</p>
     *
     * <p>Falls back to a single insert when {@code rows} contains exactly one row.</p>
     *
     * @param rows list of rows to insert; must not be null
     */
    public void insertAll(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        if (rows.size() == 1) {
            insert(rows.get(0));
            return;
        }

        // Derive column order from the first row and validate all subsequent rows
        // share the exact same key set. Silent null-padding masks schema drift and
        // has caused production data corruption in batch pipelines.
        Set<String> expectedKeys = rows.get(0).keySet();
        for (int i = 1; i < rows.size(); i++) {
            Set<String> rowKeys = rows.get(i).keySet();
            if (!rowKeys.equals(expectedKeys)) {
                throw new IllegalArgumentException(
                        "insertAll: row " + i + " has a different key set than row 0. " +
                                "Expected: " + expectedKeys + ", got: " + rowKeys + ". " +
                                "All rows must share identical columns for batch insert."
                );
            }
        }

        InsertResult first = grammar.compileInsert(table, rows.get(0));
        String sql = first.getSql();
        List<String> batchColumns = new ArrayList<>(expectedKeys);

        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
            for (Map<String, Object> row : rows) {
                List<Object> rowBindings = new ArrayList<>(batchColumns.size());
                for (String col : batchColumns) {
                    rowBindings.add(row.get(col));
                }
                bindParameters(stmt, rowBindings);
                stmt.addBatch();
            }
            stmt.executeBatch();
            QueryLog.record(sql, List.of("[batch x" + rows.size() + "]"), System.currentTimeMillis() - start);
        } catch (SQLException e) {
            QueryLog.record(sql, List.of("[batch x" + rows.size() + "]"), System.currentTimeMillis() - start);
            throw new RuntimeException("Batch insert failed on table: " + table, e);
        }
    }

    // ─── EXECUTION: UPDATE ───────────────────────────────────

    /**
     * Update matching rows.
     */
    public int update(Map<String, Object> values) {
        UpdateResult result = grammar.compileUpdate(table, values, wheres, bindings);
        return executeUpdate(result.getSql(), result.getBindings());
    }

    // ─── EXECUTION: DELETE ───────────────────────────────────

    /**
     * Delete matching rows.
     */
    public int delete() {
        DeleteResult result = grammar.compileDelete(table, wheres, bindings);
        return executeUpdate(result.getSql(), result.getBindings());
    }

    // ─── INCREMENT / DECREMENT ───────────────────────────────

    /**
     * Increments a numeric column by the given amount.
     *
     * @param column The column name — must be a valid SQL identifier
     * @param amount The amount to increment (may be negative)
     * @return The number of affected rows
     */
    public int increment(String column, int amount) {
        SqlIdentifier.requireIdentifier(column);
        String sql = grammar.compileIncrement(table, column, amount, wheres, bindings);
        return executeUpdate(sql, new ArrayList<>(bindings));
    }

    /**
     * Increments a numeric column by 1.
     *
     * @param column The column name
     * @return The number of affected rows
     */
    public int increment(String column) {
        return increment(column, 1);
    }

    /**
     * Decrements a numeric column by the given amount.
     *
     * @param column The column name
     * @param amount The amount to decrement
     * @return The number of affected rows
     */
    public int decrement(String column, int amount) {
        return increment(column, -amount);
    }

    /**
     * Decrements a numeric column by 1.
     *
     * @param column The column name
     * @return The number of affected rows
     */
    public int decrement(String column) {
        return decrement(column, 1);
    }

    // ─── RAW QUERIES ─────────────────────────────────────────

    /**
     * Executes a raw SELECT query and returns results as a list of maps.
     * The caller is responsible for ensuring {@code sql} is safe.
     *
     * @param sql    The raw SQL SELECT statement (trusted caller input only)
     * @param params Values to bind to SQL placeholders
     * @return A list of rows, each as a map of column names to values
     */
    public static List<Map<String, Object>> raw(String sql, Object... params) {
        QueryBuilder qb = new QueryBuilder("__raw__");
        return qb.executeQuery(sql, Arrays.asList(params));
    }

    /**
     * Executes a raw UPDATE/DELETE/DDL statement.
     * The caller is responsible for ensuring {@code sql} is safe.
     *
     * @param sql    Raw SQL string (trusted caller input only)
     * @param params Values to bind to SQL placeholders
     * @return The number of affected rows
     */
    public static int rawUpdate(String sql, Object... params) {
        QueryBuilder qb = new QueryBuilder("__raw__");
        return qb.executeUpdate(sql, Arrays.asList(params));
    }

    // ─── SQL COMPILATION ─────────────────────────────────────

    /**
     * Returns the compiled SQL string without executing.
     *
     * @return The SQL string
     */
    public String toSql() {
        return grammar.compileSelect(this);
    }

    /**
     * Returns an immutable view of the current bindings.
     *
     * @return The bindings
     */
    public List<Object> getBindings() {
        return Collections.unmodifiableList(bindings);
    }

    // ─── GETTERS (for Grammar) ───────────────────────────────

    /** @return The table name */
    public String getTable()               { return table; }
    /** @return The selected columns */
    public List<String> getColumns()       { return columns; }
    /** @return Whether DISTINCT is active */
    public boolean isDistinct()            { return isDistinct; }
    /** @return The WHERE clauses */
    public List<WhereClause> getWheres()   { return wheres; }
    /** @return The JOIN clauses */
    public List<JoinClause> getJoins()     { return joins; }
    /** @return The ORDER BY clauses */
    public List<OrderClause> getOrders()   { return orders; }
    /** @return The GROUP BY columns */
    public List<String> getGroups()        { return groups; }
    /** @return The HAVING clauses */
    public List<HavingClause> getHavings() { return havings; }
    /** @return The LIMIT value */
    public Integer getLimitValue()         { return limitValue; }
    /** @return The OFFSET value */
    public Integer getOffsetValue()        { return offsetValue; }

    // ─── JDBC EXECUTION ──────────────────────────────────────

    // ─── STREAMING / CHUNKED READS ──────────────────────────

    /**
     * Execute this query and process each row via {@code consumer} without loading
     * all rows into memory at once.
     *
     * <p>Uses JDBC {@code setFetchSize} to instruct the driver to stream rows from
     * the server in batches of {@code fetchSize} rather than buffering the entire
     * result set. This is the correct approach for large exports, ETL pipelines,
     * or any query whose result set could exceed available heap.</p>
     *
     * <p><b>Driver notes</b>:</p>
     * <ul>
     *   <li><b>PostgreSQL</b>: streaming requires the connection to be in a transaction
     *       (auto-commit off). Wrap with {@code DB.withTransaction()} if needed.</li>
     *   <li><b>MySQL</b>: use {@code fetchSize = Integer.MIN_VALUE} to enable row-by-row
     *       streaming; any positive value is treated as a hint and may still buffer.</li>
     *   <li><b>SQLite</b>: {@code setFetchSize} is accepted but has no effect —
     *       SQLite always fetches all rows.</li>
     * </ul>
     *
     * <p>The connection remains open for the duration of the iteration. Do not call
     * {@link DB#closeConnection()} inside {@code consumer}.</p>
     *
     * @param fetchSize number of rows to fetch per round-trip (use
     *                  {@code Integer.MIN_VALUE} for MySQL streaming)
     * @param consumer  called once per row with a {@code Map<String, Object>};
     *                  the map is reused across calls — do not retain a reference to it
     */
    public void chunk(int fetchSize, Consumer<Map<String, Object>> consumer) {
        String sql = toSql();
        List<Object> params = getBindings();

        // PostgreSQL requires an active transaction for server-side cursor streaming.
        // Without it the driver buffers the entire result set — defeating the purpose.
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
            if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
            bindParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                // Reuse a single map per iteration to reduce GC pressure.
                // The consumer must not hold a reference across calls.
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
            throw new RuntimeException("Chunk query failed: " + sql, e);
        }
    }

    /**
     * Convenience overload with a default fetch size of 1000 rows per round-trip.
     *
     * @param consumer called once per row
     */
    public void chunk(Consumer<Map<String, Object>> consumer) {
        chunk(1000, consumer);
    }

    // ─── JDBC EXECUTION ──────────────────────────────────────

    private List<Map<String, Object>> executeQuery(String sql, List<Object> params) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
            bindParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map<String, Object>> result = resultSetToList(rs);
                QueryLog.record(sql, params, System.currentTimeMillis() - start);
                return result;
            }
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }

    private Object executeInsert(String sql, List<Object> params) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
            bindParameters(stmt, params);
            stmt.executeUpdate();
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getObject(1);
                }
                return null;
            }
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            throw new RuntimeException("Insert failed: " + sql, e);
        }
    }

    private int executeUpdate(String sql, List<Object> params) {
        long start = System.currentTimeMillis();
        Connection conn = DB.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (queryTimeoutSeconds > 0) stmt.setQueryTimeout(queryTimeoutSeconds);
            bindParameters(stmt, params);
            int result = stmt.executeUpdate();
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            return result;
        } catch (SQLException e) {
            QueryLog.record(sql, params, System.currentTimeMillis() - start);
            throw new RuntimeException("Update/Delete failed: " + sql, e);
        }
    }

    private void bindParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (value instanceof String) {
                stmt.setString(i + 1, (String) value);
            } else if (value instanceof Integer) {
                stmt.setInt(i + 1, (Integer) value);
            } else if (value instanceof Long) {
                stmt.setLong(i + 1, (Long) value);
            } else if (value instanceof Double) {
                stmt.setDouble(i + 1, (Double) value);
            } else if (value instanceof Float) {
                stmt.setFloat(i + 1, (Float) value);
            } else if (value instanceof Boolean) {
                stmt.setBoolean(i + 1, (Boolean) value);
            } else if (value instanceof java.util.Date) {
                stmt.setTimestamp(i + 1, new Timestamp(((java.util.Date) value).getTime()));
            } else if (value instanceof java.time.LocalDateTime) {
                stmt.setTimestamp(i + 1, Timestamp.valueOf((java.time.LocalDateTime) value));
            } else if (value instanceof java.time.LocalDate) {
                stmt.setDate(i + 1, java.sql.Date.valueOf((java.time.LocalDate) value));
            } else {
                stmt.setObject(i + 1, value);
            }
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }
}