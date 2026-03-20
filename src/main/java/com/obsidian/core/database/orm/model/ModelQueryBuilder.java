package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.model.relation.Relation;
import com.obsidian.core.database.orm.pagination.Paginator;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * Model-aware query builder.
 * Wraps QueryBuilder and returns hydrated Model instances.
 *
 * Usage:
 *   User.query(User.class)
 *       .where("active", 1)
 *       .with("posts", "profile")
 *       .orderBy("name")
 *       .get();
 */
public class ModelQueryBuilder<T extends Model> {

    private final Class<T> modelClass;
    private final QueryBuilder queryBuilder;
    private final List<String> eagerLoads = new ArrayList<>();
    private final boolean softDeletesEnabled;
    private boolean withTrashed = false;

    /**
     * Creates a new model-aware query builder.
     *
     * @param modelClass   The model class for hydration
     * @param table        The database table name
     * @param globalScopes List of global scope functions to apply automatically
     * @param softDeletes  Whether the model uses soft deletes (auto-adds whereNull("deleted_at"))
     */
    public ModelQueryBuilder(Class<T> modelClass, String table,
                             List<Consumer<QueryBuilder>> globalScopes, boolean softDeletes) {
        this.modelClass = modelClass;
        this.softDeletesEnabled = softDeletes;
        this.queryBuilder = new QueryBuilder(table);

        // Apply global scopes
        for (Consumer<QueryBuilder> scope : globalScopes) {
            scope.accept(queryBuilder);
        }

        // Apply soft delete scope (tracked so withTrashed() can remove it)
        if (softDeletes) {
            queryBuilder.whereNull("deleted_at");
        }
    }

    // ─── DELEGATE TO QUERY BUILDER ───────────────────────────

    /**
     * Specifies which columns to retrieve.
     *
     * @param cols Column names
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> select(String... cols) {
        queryBuilder.select(cols);
        return this;
    }

    /**
     * Adds a raw expression to the SELECT clause.
     * The caller is responsible for ensuring {@code expression} is safe.
     *
     * @param expression A raw SQL expression (trusted caller input only)
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> selectRaw(String expression) {
        queryBuilder.selectRaw(expression);
        return this;
    }

    /**
     * Adds DISTINCT to the SELECT clause.
     *
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> distinct() {
        queryBuilder.distinct();
        return this;
    }

    /**
     * Adds a WHERE condition to the query.
     *
     * @param column The column name
     * @param operator The comparison operator (=, !=, >, <, >=, <=, LIKE, etc.)
     * @param value The value to compare against
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> where(String column, String operator, Object value) {
        queryBuilder.where(column, operator, value);
        return this;
    }

    /**
     * Adds a WHERE condition to the query.
     *
     * @param column The column name
     * @param value The value to compare against
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> where(String column, Object value) {
        queryBuilder.where(column, value);
        return this;
    }

    /**
     * Adds an OR WHERE condition to the query.
     *
     * @param column The column name
     * @param operator The comparison operator (=, !=, >, <, >=, <=, LIKE, etc.)
     * @param value The value to compare against
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> orWhere(String column, String operator, Object value) {
        queryBuilder.orWhere(column, operator, value);
        return this;
    }

    /**
     * Adds an OR WHERE condition to the query.
     *
     * @param column The column name
     * @param value The value to compare against
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> orWhere(String column, Object value) {
        queryBuilder.orWhere(column, value);
        return this;
    }

    /**
     * Adds a WHERE column IS NULL condition.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereNull(String column) {
        queryBuilder.whereNull(column);
        return this;
    }

    /**
     * Adds a WHERE column IS NOT NULL condition.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereNotNull(String column) {
        queryBuilder.whereNotNull(column);
        return this;
    }

    /**
     * Adds a WHERE column IN (...) condition.
     *
     * @param column The column name
     * @param values The list of values
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereIn(String column, List<?> values) {
        queryBuilder.whereIn(column, values);
        return this;
    }

    /**
     * Adds a WHERE column NOT IN (...) condition.
     *
     * @param column The column name
     * @param values The list of values
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereNotIn(String column, List<?> values) {
        queryBuilder.whereNotIn(column, values);
        return this;
    }

    /**
     * Adds a WHERE column BETWEEN low AND high condition.
     *
     * @param column The column name
     * @param low The lower bound of the range
     * @param high The upper bound of the range
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereBetween(String column, Object low, Object high) {
        queryBuilder.whereBetween(column, low, high);
        return this;
    }

    /**
     * Adds a WHERE column LIKE pattern condition.
     *
     * @param column The column name
     * @param pattern The LIKE pattern (e.g. "%john%")
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereLike(String column, String pattern) {
        queryBuilder.whereLike(column, pattern);
        return this;
    }

    /**
     * Adds a raw WHERE clause (not escaped).
     *
     * @param sql Raw SQL string
     * @param params Parameter values to bind to SQL placeholders
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> whereRaw(String sql, Object... params) {
        queryBuilder.whereRaw(sql, params);
        return this;
    }

    /**
     * Adds a WHERE condition to the query.
     *
     * @param group A callback that receives a nested QueryBuilder for grouping conditions
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> where(Consumer<QueryBuilder> group) {
        queryBuilder.where(group);
        return this;
    }

    /**
     * Adds an INNER JOIN clause to the query.
     *
     * @param table The table name
     * @param first The first column in the join condition
     * @param op The comparison operator
     * @param second The second column in the join condition
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> join(String table, String first, String op, String second) {
        queryBuilder.join(table, first, op, second);
        return this;
    }

    /**
     * Adds a LEFT JOIN clause to the query.
     *
     * @param table The table name
     * @param first The first column in the join condition
     * @param op The comparison operator
     * @param second The second column in the join condition
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> leftJoin(String table, String first, String op, String second) {
        queryBuilder.leftJoin(table, first, op, second);
        return this;
    }

    /**
     * Adds an ORDER BY clause to the query.
     *
     * @param column The column name
     * @param direction The sort direction ("ASC" or "DESC")
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> orderBy(String column, String direction) {
        queryBuilder.orderBy(column, direction);
        return this;
    }

    /**
     * Adds an ORDER BY clause to the query.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> orderBy(String column) {
        queryBuilder.orderBy(column);
        return this;
    }

    /**
     * Adds an ORDER BY ... DESC clause to the query.
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> orderByDesc(String column) {
        queryBuilder.orderByDesc(column);
        return this;
    }

    /**
     * Orders by the given column descending (default: created_at).
     *
     * @param column The column name
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> latest(String column) {
        queryBuilder.latest(column);
        return this;
    }

    /**
     * Orders by the given column descending (default: created_at).
     *
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> latest() {
        queryBuilder.latest();
        return this;
    }

    /**
     * Orders by the given column ascending (default: created_at).
     *
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> oldest() {
        queryBuilder.oldest();
        return this;
    }

    /**
     * Adds a GROUP BY clause to the query.
     *
     * @param cols Column names
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> groupBy(String... cols) {
        queryBuilder.groupBy(cols);
        return this;
    }

    /**
     * Adds a HAVING clause to the query.
     *
     * @param column The column name
     * @param op The comparison operator
     * @param value The value to compare against
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> having(String column, String op, Object value) {
        queryBuilder.having(column, op, value);
        return this;
    }

    /**
     * Sets the maximum number of rows to return.
     *
     * @param limit Maximum number of rows
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> limit(int limit) {
        queryBuilder.limit(limit);
        return this;
    }

    /**
     * Sets the number of rows to skip.
     *
     * @param offset Number of rows to skip
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> offset(int offset) {
        queryBuilder.offset(offset);
        return this;
    }

    /**
     * Sets limit and offset for pagination (page starts at 1).
     *
     * @param page Page number (starts at 1)
     * @param perPage Number of items per page
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> forPage(int page, int perPage) {
        queryBuilder.forPage(page, perPage);
        return this;
    }

    // ─── SOFT DELETE CONTROL ─────────────────────────────────

    /**
     * Include soft-deleted records in the results.
     * Removes the automatic {@code WHERE deleted_at IS NULL} filter.
     *
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> withTrashed() {
        this.withTrashed = true;
        if (softDeletesEnabled) {
            queryBuilder.removeWhereNull("deleted_at");
        }
        return this;
    }

    /**
     * Return only soft-deleted records.
     * Removes the IS NULL filter and adds IS NOT NULL.
     *
     * @return This builder instance for method chaining
     */
    public ModelQueryBuilder<T> onlyTrashed() {
        withTrashed();
        queryBuilder.whereNotNull("deleted_at");
        return this;
    }

    // ─── EAGER LOADING ───────────────────────────────────────

    /**
     * Eager load relations.
     *   User.query(User.class).with("posts", "profile").get();
     */
    public ModelQueryBuilder<T> with(String... relations) {
        eagerLoads.addAll(Arrays.asList(relations));
        return this;
    }

    // ─── SCOPES ──────────────────────────────────────────────

    /**
     * Apply a local scope.
     *   User.query(User.class).scope(User::active).get();
     */
    public ModelQueryBuilder<T> scope(Consumer<QueryBuilder> scope) {
        scope.accept(queryBuilder);
        return this;
    }

    // ─── EXECUTION ───────────────────────────────────────────

    /**
     * Execute query and return hydrated models.
     */
    public List<T> get() {
        List<Map<String, Object>> rows = queryBuilder.get();
        List<T> models = Model.hydrateList(modelClass, rows);

        // Eager load relations
        if (!eagerLoads.isEmpty() && !models.isEmpty()) {
            eagerLoadRelations(models);
        }

        return models;
    }

    /**
     * Get first result or null.
     */
    public T first() {
        queryBuilder.limit(1);
        List<T> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * First or throw.
     */
    public T firstOrFail() {
        T model = first();
        if (model == null) {
            throw new ModelNotFoundException(modelClass.getSimpleName() + " not found");
        }
        return model;
    }

    /**
     * Find by ID.
     */
    public T find(Object id) {
        T instance = Model.newInstance(modelClass);
        return where(instance.primaryKey(), id).first();
    }

    /**
     * Get a single column as list.
     */
    public List<Object> pluck(String column) {
        return queryBuilder.pluck(column);
    }

    /**
     * Count.
     */
    public long count() {
        return queryBuilder.count();
    }

    /**
     * Checks if any rows match the query.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean exists() {
        return queryBuilder.exists();
    }

    /**
     * Checks if no rows match the query.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean doesntExist() {
        return queryBuilder.doesntExist();
    }

    /**
     * Aggregates.
     */
    public Object max(String column) { return queryBuilder.max(column); }
    /**
     * Returns the minimum value of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object min(String column) { return queryBuilder.min(column); }
    /**
     * Returns the sum of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object sum(String column) { return queryBuilder.sum(column); }
    /**
     * Returns the average of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object avg(String column) { return queryBuilder.avg(column); }

    /**
     * Update matching rows.
     */
    public int update(Map<String, Object> values) {
        return queryBuilder.update(values);
    }

    /**
     * Delete matching rows.
     */
    public int delete() {
        return queryBuilder.delete();
    }

    /**
     * Paginate results without mutating this builder.
     *
     * <p>The previous implementation called {@code count()} then {@code forPage()} on the same
     * underlying {@link QueryBuilder}, permanently adding LIMIT/OFFSET to its state. Any
     * subsequent call on the same builder (e.g. a second {@code paginate()} or a {@code get()})
     * would silently return wrong results.</p>
     *
     * <p>This implementation keeps the original builder untouched:</p>
     * <ol>
     *   <li>The total count is obtained via {@code count()} — which already uses a separate
     *       aggregate query internally and does not mutate the builder.</li>
     *   <li>A fresh page query is built by copying the current SQL and bindings into a new
     *       raw {@link QueryBuilder}, then applying LIMIT/OFFSET only there.</li>
     * </ol>
     *
     * @param page    page number, starting at 1
     * @param perPage number of items per page
     * @return a {@link Paginator} with items and metadata
     */
    public Paginator<T> paginate(int page, int perPage) {
        // Step 1: total count — non-mutating (aggregateValue runs a separate query internally)
        long total = count();

        // Step 2: fetch the page using a fresh builder scoped to this page only.
        // We re-use toSql() + bindings so all WHERE/JOIN/ORDER clauses are preserved,
        // then wrap in a raw QueryBuilder and apply LIMIT/OFFSET without touching `this`.
        String baseSql = queryBuilder.toSql();
        List<Object> baseBindings = new ArrayList<>(queryBuilder.getBindings());

        int offset = (page - 1) * perPage;
        String pageSql = baseSql + " LIMIT " + perPage + " OFFSET " + offset;

        List<Map<String, Object>> rows = QueryBuilder.raw(pageSql,
                baseBindings.toArray());
        List<T> items = Model.hydrateList(modelClass, rows);

        if (!eagerLoads.isEmpty() && !items.isEmpty()) {
            eagerLoadRelations(items);
        }

        return new Paginator<>(items, total, perPage, page);
    }

    /**
     * Paginate with the default page size of 15.
     *
     * @param page page number, starting at 1
     * @return a {@link Paginator} with items and metadata
     */
    public Paginator<T> paginate(int page) {
        return paginate(page, 15);
    }

    /**
     * Get the raw SQL.
     */
    public String toSql() {
        return queryBuilder.toSql();
    }

    /**
     * Returns the query builder.
     *
     * @return The query builder
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    // ─── EAGER LOADING LOGIC ─────────────────────────────────

    /**
     * Cache of reflected relation methods per model class.
     * Avoids repeated getDeclaredMethod() calls on the same class.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Method> relationMethodCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private void eagerLoadRelations(List<T> models) {
        for (String relationName : eagerLoads) {
            try {
                // Cache key: ClassName.relationName
                String cacheKey = modelClass.getName() + "." + relationName;
                Method method = relationMethodCache.computeIfAbsent(cacheKey, k -> {
                    try {
                        Method m = modelClass.getDeclaredMethod(relationName);
                        m.setAccessible(true);
                        return m;
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("Relation method '" + relationName
                                + "' not found on " + modelClass.getSimpleName());
                    }
                });

                T sample = models.get(0);
                Object relation = method.invoke(sample);

                if (relation instanceof Relation) {
                    ((Relation<?>) relation).eagerLoad(models, relationName);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to eager load relation: " + relationName, e);
            }
        }
    }
}