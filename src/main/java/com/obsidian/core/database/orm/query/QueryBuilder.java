package com.obsidian.core.database.orm.query;

import com.obsidian.core.database.orm.query.clause.*;
import com.obsidian.core.database.orm.query.grammar.*;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Fluent query builder for constructing and executing SQL queries.
 *
 * @see QueryExecutor
 * @see QueryAggregates
 */
public class QueryBuilder {

    private final String table;
    private final Grammar grammar;
    private final QueryExecutor executor;

    private final List<String>       columns    = new ArrayList<>();
    private boolean                  isDistinct = false;
    private final List<WhereClause>  wheres     = new ArrayList<>();
    private final List<JoinClause>   joins      = new ArrayList<>();
    private final List<OrderClause>  orders     = new ArrayList<>();
    private final List<String>       groups     = new ArrayList<>();
    private final List<HavingClause> havings    = new ArrayList<>();
    private Integer limitValue  = null;
    private Integer offsetValue = null;
    private final List<Object> bindings   = new ArrayList<>();
    private final List<String> eagerLoads = new ArrayList<>();
    private int queryTimeoutSeconds = 30;

    // ─── CONSTRUCTORS ────────────────────────────────────────

    /**
     * Creates a builder for the given table using the default grammar.
     *
     * @param table table name — must be a valid SQL identifier
     */
    public QueryBuilder(String table) {
        this(table, GrammarFactory.get());
    }

    /**
     * Creates a builder for the given table and grammar.
     *
     * @param table   table name — must be a valid SQL identifier
     * @param grammar SQL grammar to use for compilation
     */
    public QueryBuilder(String table, Grammar grammar) {
        if (!table.startsWith("__")) {
            SqlIdentifier.requireIdentifier(table);
        }
        this.table    = table;
        this.grammar  = grammar;
        this.executor = new QueryExecutor(queryTimeoutSeconds);
    }

    // ─── SELECT ──────────────────────────────────────────────

    /**
     * Adds columns to the SELECT clause.
     *
     * @param cols column names — must be valid SQL identifiers
     * @return this builder
     */
    public QueryBuilder select(String... cols) {
        for (String col : cols) SqlIdentifier.requireIdentifier(col);
        columns.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds a raw expression to the SELECT clause.
     *
     * @param expression raw SQL expression — caller must ensure safety
     * @return this builder
     */
    public QueryBuilder selectRaw(String expression) {
        columns.add(new RawExpression(expression).toString());
        return this;
    }

    /**
     * Adds DISTINCT to the SELECT clause.
     *
     * @return this builder
     */
    public QueryBuilder distinct() {
        this.isDistinct = true;
        return this;
    }

    // ─── WHERE ───────────────────────────────────────────────

    /**
     * Adds an AND WHERE condition.
     *
     * @param column   column name — must be a valid SQL identifier
     * @param operator comparison operator — must be in the allowed whitelist
     * @param value    value bound via PreparedStatement
     * @return this builder
     */
    public QueryBuilder where(String column, String operator, Object value) {
        SqlIdentifier.requireIdentifier(column);
        SqlIdentifier.requireOperator(operator);
        wheres.add(new WhereClause(column, operator, value, "AND"));
        bindings.add(value);
        return this;
    }

    /**
     * Adds an AND WHERE column = value condition.
     *
     * @param column column name
     * @param value  value to compare against
     * @return this builder
     */
    public QueryBuilder where(String column, Object value) {
        return where(column, "=", value);
    }

    /**
     * Adds an OR WHERE condition.
     *
     * @param column   column name
     * @param operator comparison operator
     * @param value    value bound via PreparedStatement
     * @return this builder
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
     * @param column column name
     * @param value  value to compare against
     * @return this builder
     */
    public QueryBuilder orWhere(String column, Object value) {
        return orWhere(column, "=", value);
    }

    /**
     * Adds a WHERE column IS NULL condition.
     *
     * @param column column name
     * @return this builder
     */
    public QueryBuilder whereNull(String column) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.isNull(column, "AND"));
        return this;
    }

    /**
     * Adds a WHERE column IS NOT NULL condition.
     *
     * @param column column name
     * @return this builder
     */
    public QueryBuilder whereNotNull(String column) {
        SqlIdentifier.requireIdentifier(column);
        wheres.add(WhereClause.isNotNull(column, "AND"));
        return this;
    }

    /**
     * Removes a previously added IS NULL clause for the given column.
     *
     * @param column column to remove the null check for
     * @return this builder
     */
    public QueryBuilder removeWhereNull(String column) {
        wheres.removeIf(w -> w.getType() == WhereClause.Type.NULL && column.equals(w.getColumn()));
        return this;
    }

    /**
     * Adds a WHERE column IN (...) condition.
     *
     * @param column column name
     * @param values values bound via PreparedStatement
     * @return this builder
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
     * @param column column name
     * @param values values bound via PreparedStatement
     * @return this builder
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
     * @param column column name
     * @param low    lower bound
     * @param high   upper bound
     * @return this builder
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
     * @param column  column name
     * @param pattern LIKE pattern bound as a value
     * @return this builder
     */
    public QueryBuilder whereLike(String column, String pattern) {
        return where(column, "LIKE", pattern);
    }

    /**
     * Adds a raw WHERE clause.
     *
     * @param sql    raw SQL string — caller must ensure safety
     * @param params values to bind to placeholders
     * @return this builder
     */
    public QueryBuilder whereRaw(String sql, Object... params) {
        wheres.add(WhereClause.raw(sql, "AND"));
        bindings.addAll(Arrays.asList(params));
        return this;
    }

    /**
     * Adds a nested AND WHERE group.
     *
     * @param group callback receiving a nested builder
     * @return this builder
     */
    public QueryBuilder where(Consumer<QueryBuilder> group) {
        QueryBuilder nested = new QueryBuilder(this.table, this.grammar);
        group.accept(nested);
        wheres.add(WhereClause.nested(nested, "AND"));
        bindings.addAll(nested.getBindings());
        return this;
    }

    /**
     * Adds a nested OR WHERE group.
     *
     * @param group callback receiving a nested builder
     * @return this builder
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
     * Adds an INNER JOIN clause.
     *
     * @param table    joined table name
     * @param first    left-hand column
     * @param operator join operator
     * @param second   right-hand column
     * @return this builder
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
     * Adds a LEFT JOIN clause.
     *
     * @param table    joined table name
     * @param first    left-hand column
     * @param operator join operator
     * @param second   right-hand column
     * @return this builder
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
     * Adds a RIGHT JOIN clause.
     *
     * @param table    joined table name
     * @param first    left-hand column
     * @param operator join operator
     * @param second   right-hand column
     * @return this builder
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
     * Adds a CROSS JOIN clause.
     *
     * @param table joined table name
     * @return this builder
     */
    public QueryBuilder crossJoin(String table) {
        SqlIdentifier.requireIdentifier(table);
        joins.add(new JoinClause("CROSS", table, null, null, null));
        return this;
    }

    // ─── ORDER BY ────────────────────────────────────────────

    /**
     * Adds an ORDER BY clause.
     *
     * @param column    column name
     * @param direction ASC or DESC
     * @return this builder
     */
    public QueryBuilder orderBy(String column, String direction) {
        SqlIdentifier.requireIdentifier(column);
        SqlIdentifier.requireDirection(direction);
        orders.add(new OrderClause(column, direction.toUpperCase()));
        return this;
    }

    /** @param column column name — orders ASC */
    public QueryBuilder orderBy(String column)    { return orderBy(column, "ASC"); }
    /** @param column column name — orders DESC */
    public QueryBuilder orderByDesc(String column) { return orderBy(column, "DESC"); }
    /** @param column column name — orders DESC */
    public QueryBuilder latest(String column)      { return orderByDesc(column); }
    /** Orders by created_at DESC. */
    public QueryBuilder latest()                   { return latest("created_at"); }
    /** @param column column name — orders ASC */
    public QueryBuilder oldest(String column)      { return orderBy(column, "ASC"); }
    /** Orders by created_at ASC. */
    public QueryBuilder oldest()                   { return oldest("created_at"); }

    // ─── GROUP BY / HAVING ───────────────────────────────────

    /**
     * Adds a GROUP BY clause.
     *
     * @param cols column names
     * @return this builder
     */
    public QueryBuilder groupBy(String... cols) {
        for (String col : cols) SqlIdentifier.requireIdentifier(col);
        groups.addAll(Arrays.asList(cols));
        return this;
    }

    /**
     * Adds a HAVING condition.
     *
     * @param column   column name
     * @param operator comparison operator
     * @param value    value bound via PreparedStatement
     * @return this builder
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
     *
     * @param sql    raw SQL string — caller must ensure safety
     * @param params values to bind to placeholders
     * @return this builder
     */
    public QueryBuilder havingRaw(String sql, Object... params) {
        havings.add(HavingClause.raw(sql));
        bindings.addAll(Arrays.asList(params));
        return this;
    }

    // ─── LIMIT / OFFSET ──────────────────────────────────────

    /** @param limit max rows to return */
    public QueryBuilder limit(int limit)   { this.limitValue  = limit;  return this; }
    /** @param offset rows to skip */
    public QueryBuilder offset(int offset) { this.offsetValue = offset; return this; }
    /** @param n max rows — alias for limit */
    public QueryBuilder take(int n)        { return limit(n); }
    /** @param n rows to skip — alias for offset */
    public QueryBuilder skip(int n)        { return offset(n); }

    /**
     * Sets the statement-level query timeout.
     *
     * @param seconds timeout in seconds, 0 to disable
     * @return this builder
     */
    public QueryBuilder timeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
        return this;
    }

    /**
     * Applies LIMIT and OFFSET for the given page.
     *
     * @param page    page number starting at 1
     * @param perPage items per page
     * @return this builder
     */
    public QueryBuilder forPage(int page, int perPage) {
        return limit(perPage).offset((page - 1) * perPage);
    }

    // ─── EAGER LOADING ───────────────────────────────────────

    /**
     * Specifies relations to eager-load with the query results.
     *
     * @param relations relation method names
     * @return this builder
     */
    public QueryBuilder with(String... relations) {
        eagerLoads.addAll(Arrays.asList(relations));
        return this;
    }

    /** @return immutable list of eager-load relation names */
    public List<String> getEagerLoads() {
        return Collections.unmodifiableList(eagerLoads);
    }

    // ─── SCOPES ──────────────────────────────────────────────

    /**
     * Applies a scope function to this builder.
     *
     * @param scope scope function to apply
     * @return this builder
     */
    public QueryBuilder applyScope(Consumer<QueryBuilder> scope) {
        scope.accept(this);
        return this;
    }

    // ─── SELECT EXECUTION ────────────────────────────────────

    /** @return all matching rows */
    public List<Map<String, Object>> get() {
        return executor.executeQuery(toSql(), getBindings());
    }

    /** @return first matching row, or null */
    public Map<String, Object> first() {
        limit(1);
        List<Map<String, Object>> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Finds a row by primary key.
     *
     * @param id primary key value
     * @return matching row, or null
     */
    public Map<String, Object> find(Object id) {
        return where("id", id).first();
    }

    /**
     * Returns a single column's values as a list.
     *
     * @param column column name
     * @return list of values
     */
    public List<Object> pluck(String column) {
        SqlIdentifier.requireIdentifier(column);
        columns.clear();
        columns.add(column);
        return get().stream().map(row -> row.get(column)).collect(Collectors.toList());
    }

    /**
     * Returns true if any row matches the current WHERE clauses.
     *
     * @return true if at least one row exists
     */
    public boolean exists() {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM ").append(table);
        for (JoinClause join : joins) sql.append(" ").append(join.toSql());
        String where = grammar.compileWheres(wheres);
        if (!where.isEmpty()) sql.append(" WHERE ").append(where);
        sql.append(" LIMIT 1");
        return !executor.executeQuery(sql.toString(), new ArrayList<>(bindings)).isEmpty();
    }

    /** @return true if no rows match */
    public boolean doesntExist() {
        return !exists();
    }

    // ─── AGGREGATES ──────────────────────────────────────────

    /** @return count of all matching rows */
    public long count() { return QueryAggregates.count(this, executor, grammar, "*"); }

    /**
     * Returns count of non-null values in the given column.
     *
     * @param column column name
     * @return count of non-null values
     */
    public long count(String column) {
        SqlIdentifier.requireIdentifier(column);
        return QueryAggregates.count(this, executor, grammar, column);
    }

    /** @param column column name */
    public Object max(String column) { SqlIdentifier.requireIdentifier(column); return QueryAggregates.aggregate(this, executor, grammar, "MAX", column); }
    /** @param column column name */
    public Object min(String column) { SqlIdentifier.requireIdentifier(column); return QueryAggregates.aggregate(this, executor, grammar, "MIN", column); }
    /** @param column column name */
    public Object sum(String column) { SqlIdentifier.requireIdentifier(column); return QueryAggregates.aggregate(this, executor, grammar, "SUM", column); }
    /** @param column column name */
    public Object avg(String column) { SqlIdentifier.requireIdentifier(column); return QueryAggregates.aggregate(this, executor, grammar, "AVG", column); }

    // ─── INSERT ──────────────────────────────────────────────

    /**
     * Inserts a single row and returns the generated key.
     *
     * @param values column-to-value map
     * @return generated key, or null
     */
    public Object insert(Map<String, Object> values) {
        InsertResult result = grammar.compileInsert(table, values);
        return executor.executeInsert(result.getSql(), result.getBindings());
    }

    /**
     * Inserts multiple rows in a single JDBC batch.
     *
     * @param rows rows to insert — all must share the same key set
     */
    public void insertAll(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        if (rows.size() == 1) { insert(rows.get(0)); return; }

        Set<String> expectedKeys = rows.get(0).keySet();
        for (String col : expectedKeys) SqlIdentifier.requireIdentifier(col);

        for (int i = 1; i < rows.size(); i++) {
            if (!rows.get(i).keySet().equals(expectedKeys)) {
                throw new IllegalArgumentException(
                        "insertAll: row " + i + " has a different key set than row 0. " +
                                "Expected: " + expectedKeys + ", got: " + rows.get(i).keySet());
            }
        }

        InsertResult first = grammar.compileInsert(table, rows.get(0));
        List<String> batchColumns = new ArrayList<>(rows.get(0).keySet());
        executor.executeBatch(table, first.getSql(), batchColumns, rows);
    }

    // ─── UPDATE / DELETE ─────────────────────────────────────

    /**
     * Updates matching rows.
     *
     * @param values column-to-value map
     * @return number of affected rows
     */
    public int update(Map<String, Object> values) {
        UpdateResult result = grammar.compileUpdate(table, values, wheres, bindings);
        return executor.executeUpdate(result.getSql(), result.getBindings());
    }

    /**
     * Deletes matching rows.
     *
     * @return number of affected rows
     */
    public int delete() {
        DeleteResult result = grammar.compileDelete(table, wheres, bindings);
        return executor.executeUpdate(result.getSql(), result.getBindings());
    }

    // ─── INCREMENT / DECREMENT ───────────────────────────────

    /**
     * Atomically increments a numeric column.
     *
     * @param column column name
     * @param amount increment amount (negative to decrement)
     * @return number of affected rows
     */
    public int increment(String column, int amount) {
        SqlIdentifier.requireIdentifier(column);
        String sql = grammar.compileIncrement(table, column, amount, wheres, bindings);
        return executor.executeUpdate(sql, new ArrayList<>(bindings));
    }

    /** @param column column name */
    public int increment(String column)             { return increment(column, 1); }
    /** @param column column name @param amount decrement amount */
    public int decrement(String column, int amount) { return increment(column, -amount); }
    /** @param column column name */
    public int decrement(String column)             { return decrement(column, 1); }

    // ─── STREAMING ───────────────────────────────────────────

    /**
     * Streams rows without loading the full result set into memory.
     *
     * @param fetchSize rows per round-trip (Integer.MIN_VALUE for MySQL streaming)
     * @param consumer  called once per row — do not retain the map reference across calls
     */
    public void chunk(int fetchSize, Consumer<Map<String, Object>> consumer) {
        executor.executeChunk(toSql(), getBindings(), fetchSize, consumer);
    }

    /**
     * Streams rows with a default fetch size of 1000.
     *
     * @param consumer called once per row
     */
    public void chunk(Consumer<Map<String, Object>> consumer) {
        chunk(1000, consumer);
    }

    // ─── RAW ─────────────────────────────────────────────────

    /**
     * Executes a raw SELECT query.
     *
     * @param sql    raw SQL — caller must ensure safety
     * @param params values to bind to placeholders
     * @return list of rows
     */
    public static List<Map<String, Object>> raw(String sql, Object... params) {
        return new QueryBuilder("__raw__").executor.executeQuery(sql, Arrays.asList(params));
    }

    /**
     * Executes a raw UPDATE/DELETE/DDL statement.
     *
     * @param sql    raw SQL — caller must ensure safety
     * @param params values to bind to placeholders
     * @return number of affected rows
     */
    public static int rawUpdate(String sql, Object... params) {
        return new QueryBuilder("__raw__").executor.executeUpdate(sql, Arrays.asList(params));
    }

    // ─── COMPILATION ─────────────────────────────────────────

    /** @return compiled SQL string without executing */
    public String toSql() { return grammar.compileSelect(this); }

    /** @return immutable view of the current bindings */
    public List<Object> getBindings() { return Collections.unmodifiableList(bindings); }

    // ─── GETTERS ─────────────────────────────────────────────

    public String             getTable()       { return table; }
    public List<String>       getColumns()     { return columns; }
    public boolean            isDistinct()     { return isDistinct; }
    public List<WhereClause>  getWheres()      { return wheres; }
    public List<JoinClause>   getJoins()       { return joins; }
    public List<OrderClause>  getOrders()      { return orders; }
    public List<String>       getGroups()      { return groups; }
    public List<HavingClause> getHavings()     { return havings; }
    public Integer            getLimitValue()  { return limitValue; }
    public Integer            getOffsetValue() { return offsetValue; }
}