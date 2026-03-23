package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.model.relation.Relation;
import com.obsidian.core.database.orm.model.Table;
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
public class ModelQueryBuilder<T extends Model>
{
    private final Class<T> modelClass;
    private final QueryBuilder queryBuilder;
    private final List<String> eagerLoads = new ArrayList<>();
    private final boolean softDeletesEnabled;
    private boolean withTrashed = false;

    /**
     * Creates a new model-aware query builder.
     *
     * @param modelClass   model class used for hydration
     * @param table        database table name
     * @param globalScopes global scope functions applied automatically
     * @param softDeletes  whether the model uses soft deletes
     */
    public ModelQueryBuilder(Class<T> modelClass, String table, List<Consumer<QueryBuilder>> globalScopes, boolean softDeletes)
    {
        this.modelClass = modelClass;
        this.softDeletesEnabled = softDeletes;
        this.queryBuilder = new QueryBuilder(table);

        for (Consumer<QueryBuilder> scope : globalScopes) {
            scope.accept(queryBuilder);
        }

        if (softDeletes) {
            queryBuilder.whereNull("deleted_at");
        }
    }

    /**
     * Specifies which columns to retrieve.
     *
     * @param cols column names to select
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> select(String... cols) {
        queryBuilder.select(cols);
        return this;
    }

    /**
     * Adds a raw expression to the SELECT clause.
     * The caller is responsible for ensuring {@code expression} is safe.
     *
     * @param expression raw SQL expression
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> selectRaw(String expression) {
        queryBuilder.selectRaw(expression);
        return this;
    }

    /**
     * Adds DISTINCT to the SELECT clause.
     *
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> distinct() {
        queryBuilder.distinct();
        return this;
    }

    /**
     * Adds a {@code WHERE column op value} condition.
     *
     * @param column   column name
     * @param operator comparison operator
     * @param value    value to compare against
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> where(String column, String operator, Object value) {
        queryBuilder.where(column, operator, value);
        return this;
    }

    /**
     * Adds a {@code WHERE column = value} condition.
     *
     * @param column column name
     * @param value  value to match
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> where(String column, Object value) {
        queryBuilder.where(column, value);
        return this;
    }

    /**
     * Adds an {@code OR WHERE column op value} condition.
     *
     * @param column   column name
     * @param operator comparison operator
     * @param value    value to compare against
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> orWhere(String column, String operator, Object value) {
        queryBuilder.orWhere(column, operator, value);
        return this;
    }

    /**
     * Adds an {@code OR WHERE column = value} condition.
     *
     * @param column column name
     * @param value  value to match
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> orWhere(String column, Object value) {
        queryBuilder.orWhere(column, value);
        return this;
    }

    /**
     * Adds a {@code WHERE column IS NULL} condition.
     *
     * @param column column name
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereNull(String column) {
        queryBuilder.whereNull(column);
        return this;
    }

    /**
     * Adds a {@code WHERE column IS NOT NULL} condition.
     *
     * @param column column name
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereNotNull(String column) {
        queryBuilder.whereNotNull(column);
        return this;
    }

    /**
     * Adds a {@code WHERE column IN (...)} condition.
     *
     * @param column column name
     * @param values list of allowed values
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereIn(String column, List<?> values) {
        queryBuilder.whereIn(column, values);
        return this;
    }

    /**
     * Adds a {@code WHERE column NOT IN (...)} condition.
     *
     * @param column column name
     * @param values list of excluded values
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereNotIn(String column, List<?> values) {
        queryBuilder.whereNotIn(column, values);
        return this;
    }

    /**
     * Adds a {@code WHERE column BETWEEN low AND high} condition.
     *
     * @param column column name
     * @param low    lower bound
     * @param high   upper bound
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereBetween(String column, Object low, Object high) {
        queryBuilder.whereBetween(column, low, high);
        return this;
    }

    /**
     * Adds a {@code WHERE column LIKE pattern} condition.
     *
     * @param column  column name
     * @param pattern LIKE pattern
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereLike(String column, String pattern) {
        queryBuilder.whereLike(column, pattern);
        return this;
    }

    /**
     * Adds a raw WHERE clause. The caller is responsible for safety.
     *
     * @param sql    raw SQL condition
     * @param params parameter values bound to placeholders
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereRaw(String sql, Object... params) {
        queryBuilder.whereRaw(sql, params);
        return this;
    }

    /**
     * Adds a grouped WHERE condition via a nested builder callback.
     *
     * @param group callback receiving a nested {@link QueryBuilder}
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> where(Consumer<QueryBuilder> group) {
        queryBuilder.where(group);
        return this;
    }

    /**
     * Adds a WHERE EXISTS subquery for a BelongsTo relation.
     *
     * @param relatedClass    related model class (must have @Table annotation)
     * @param relatedKey      primary key column on the related table
     * @param foreignKey      foreign key column on this model's table
     * @param constraints     callback to add conditions on the related table query
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> whereHas(Class<? extends Model> relatedClass, String relatedKey, String foreignKey, Consumer<QueryBuilder> constraints)
    {
        Table tableAnnotation = relatedClass.getAnnotation(Table.class);
        if (tableAnnotation == null) {
            throw new IllegalArgumentException("Class " + relatedClass.getSimpleName() + " has no @Table annotation");
        }
        String relatedTable = tableAnnotation.value();

        QueryBuilder sub = new QueryBuilder(relatedTable);
        sub.whereRaw(relatedTable + "." + relatedKey + " = " + queryBuilder.getTable() + "." + foreignKey);
        constraints.accept(sub);

        String subSql = sub.toSql().replace("SELECT *", "SELECT 1");
        List<Object> subBindings = sub.getBindings();

        queryBuilder.whereRaw("EXISTS (" + subSql + ")", subBindings.toArray());
        return this;
    }

    /**
     * Adds an INNER JOIN clause.
     *
     * @param table  table to join
     * @param first  first column in the join condition
     * @param op     comparison operator
     * @param second second column in the join condition
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> join(String table, String first, String op, String second) {
        queryBuilder.join(table, first, op, second);
        return this;
    }

    /**
     * Adds a LEFT JOIN clause.
     *
     * @param table  table to join
     * @param first  first column in the join condition
     * @param op     comparison operator
     * @param second second column in the join condition
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> leftJoin(String table, String first, String op, String second) {
        queryBuilder.leftJoin(table, first, op, second);
        return this;
    }

    /**
     * Adds an ORDER BY clause.
     *
     * @param column    column name
     * @param direction sort direction, {@code "ASC"} or {@code "DESC"}
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> orderBy(String column, String direction) {
        queryBuilder.orderBy(column, direction);
        return this;
    }

    /**
     * Adds an ascending ORDER BY clause.
     *
     * @param column column name
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> orderBy(String column) {
        queryBuilder.orderBy(column);
        return this;
    }

    /**
     * Adds a descending ORDER BY clause.
     *
     * @param column column name
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> orderByDesc(String column) {
        queryBuilder.orderByDesc(column);
        return this;
    }

    /**
     * Orders by the given column descending.
     *
     * @param column column name
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> latest(String column) {
        queryBuilder.latest(column);
        return this;
    }

    /**
     * Orders by {@code created_at} descending.
     *
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> latest() {
        queryBuilder.latest();
        return this;
    }

    /**
     * Orders by {@code created_at} ascending.
     *
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> oldest() {
        queryBuilder.oldest();
        return this;
    }

    /**
     * Adds a GROUP BY clause.
     *
     * @param cols column names to group by
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> groupBy(String... cols) {
        queryBuilder.groupBy(cols);
        return this;
    }

    /**
     * Adds a HAVING clause.
     *
     * @param column column name
     * @param op     comparison operator
     * @param value  value to compare against
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> having(String column, String op, Object value) {
        queryBuilder.having(column, op, value);
        return this;
    }

    /**
     * Sets the maximum number of rows to return.
     *
     * @param limit maximum row count
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> limit(int limit) {
        queryBuilder.limit(limit);
        return this;
    }

    /**
     * Sets the number of rows to skip.
     *
     * @param offset number of rows to skip
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> offset(int offset) {
        queryBuilder.offset(offset);
        return this;
    }

    /**
     * Applies LIMIT and OFFSET for the given page number. Pages start at 1.
     *
     * @param page    page number
     * @param perPage number of items per page
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> forPage(int page, int perPage) {
        queryBuilder.forPage(page, perPage);
        return this;
    }

    /**
     * Includes soft-deleted records by removing the automatic {@code WHERE deleted_at IS NULL} filter.
     *
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> withTrashed()
    {
        this.withTrashed = true;
        if (softDeletesEnabled) {
            queryBuilder.removeWhereNull("deleted_at");
        }
        return this;
    }

    /**
     * Returns only soft-deleted records by replacing the IS NULL filter with IS NOT NULL.
     *
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> onlyTrashed()
    {
        withTrashed();
        queryBuilder.whereNotNull("deleted_at");
        return this;
    }

    /**
     * Registers relations to eager load with the results.
     *
     * @param relations relation method names to eager load
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> with(String... relations) {
        eagerLoads.addAll(Arrays.asList(relations));
        return this;
    }

    /**
     * Applies a local scope to the underlying query builder.
     *
     * @param scope scope callback receiving the underlying {@link QueryBuilder}
     * @return this builder for chaining
     */
    public ModelQueryBuilder<T> scope(Consumer<QueryBuilder> scope) {
        scope.accept(queryBuilder);
        return this;
    }

    /**
     * Executes the query and returns hydrated model instances.
     * Results are cached when the model class carries {@link Cacheable}.
     *
     * @return list of hydrated model instances
     */
    @SuppressWarnings("unchecked")
    public List<T> get() {
        if (!ModelCache.isEnabled(modelClass)) {
            return executeGet();
        }

        String key = ModelCache.queryKey(modelClass,
                queryBuilder.toSql(), queryBuilder.getBindings());
        return (List<T>) ModelCache.rememberList(modelClass, key, this::executeGet);
    }

    private List<T> executeGet() {
        List<Map<String, Object>> rows = queryBuilder.get();
        List<T> models = Model.hydrateList(modelClass, rows);
        if (!eagerLoads.isEmpty() && !models.isEmpty()) {
            eagerLoadRelations(models);
        }
        return models;
    }

    /**
     * Executes the query and returns the first result, or {@code null} if none.
     *
     * @return first model instance, or {@code null}
     */
    public T first() {
        queryBuilder.limit(1);
        List<T> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Executes the query and returns the first result, throwing if none is found.
     *
     * @return first model instance
     * @throws ModelNotFoundException if no record matches
     */
    public T firstOrFail() {
        T model = first();
        if (model == null) {
            throw new ModelNotFoundException(modelClass.getSimpleName() + " not found");
        }
        return model;
    }

    /**
     * Finds a model by primary key.
     *
     * @param id primary key value
     * @return matching model instance, or {@code null}
     */
    public T find(Object id) {
        T instance = Model.newInstance(modelClass);
        return where(instance.primaryKey(), id).first();
    }

    /**
     * Returns the values of a single column across all matching rows.
     *
     * @param column column name to extract
     * @return list of column values
     */
    public List<Object> pluck(String column) {
        return queryBuilder.pluck(column);
    }

    /**
     * Returns the number of matching rows.
     *
     * @return row count
     */
    public long count() {
        return queryBuilder.count();
    }

    /**
     * Returns {@code true} if at least one row matches the query.
     *
     * @return {@code true} if a match exists
     */
    public boolean exists() {
        return queryBuilder.exists();
    }

    /**
     * Returns {@code true} if no rows match the query.
     *
     * @return {@code true} if no match exists
     */
    public boolean doesntExist() {
        return queryBuilder.doesntExist();
    }

    /**
     * Returns the maximum value of the given column.
     *
     * @param column column name
     * @return maximum value, or {@code null}
     */
    public Object max(String column) { return queryBuilder.max(column); }

    /**
     * Returns the minimum value of the given column.
     *
     * @param column column name
     * @return minimum value, or {@code null}
     */
    public Object min(String column) { return queryBuilder.min(column); }

    /**
     * Returns the sum of the given column.
     *
     * @param column column name
     * @return sum value, or {@code null}
     */
    public Object sum(String column) { return queryBuilder.sum(column); }

    /**
     * Returns the average of the given column.
     *
     * @param column column name
     * @return average value, or {@code null}
     */
    public Object avg(String column) { return queryBuilder.avg(column); }

    /**
     * Updates matching rows with the given values.
     *
     * @param values column-value pairs to apply
     * @return number of affected rows
     */
    public int update(Map<String, Object> values) {
        return queryBuilder.update(values);
    }

    /**
     * Deletes all matching rows.
     *
     * @return number of affected rows
     */
    public int delete() {
        return queryBuilder.delete();
    }

    /**
     * Paginates results without mutating this builder.
     * The total count is obtained via a separate aggregate query, and the page
     * rows are fetched by appending LIMIT/OFFSET to a copy of the current SQL.
     *
     * @param page    page number, starting at 1
     * @param perPage number of items per page
     * @return a {@link Paginator} containing items and metadata
     */
    public Paginator<T> paginate(int page, int perPage)
    {
        long total = count();

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
     * Paginates results with the default page size of 15.
     *
     * @param page page number, starting at 1
     * @return a {@link Paginator} containing items and metadata
     */
    public Paginator<T> paginate(int page) {
        return paginate(page, 15);
    }

    /**
     * Returns the raw SQL string for this query.
     *
     * @return SQL string
     */
    public String toSql() {
        return queryBuilder.toSql();
    }

    /**
     * Returns the underlying {@link QueryBuilder}.
     *
     * @return query builder instance
     */
    public QueryBuilder getQueryBuilder() {
        return queryBuilder;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Method> relationMethodCache = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private void eagerLoadRelations(List<T> models)
    {
        for (String relationName : eagerLoads) {
            try {
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