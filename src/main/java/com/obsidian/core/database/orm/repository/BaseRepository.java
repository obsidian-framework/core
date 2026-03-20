package com.obsidian.core.database.orm.repository;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.model.ModelQueryBuilder;
import com.obsidian.core.database.orm.model.ModelNotFoundException;
import com.obsidian.core.database.orm.pagination.Paginator;

import java.util.List;
import java.util.Map;

/**
 * Generic base repository.
 * Provides all common CRUD operations out of the box.
 *
 * Usage:
 *   @Repository
 *   public class UserRepository extends BaseRepository<User> {
 *
 *       public UserRepository() {
 *           super(User.class);
 *       }
 *
 *       // Add custom queries
 *       public List<User> findActive() {
 *           return query().where("active", 1).get();
 *       }
 *   }
 *
 * Or minimal:
 *   @Repository
 *   public class PostRepository extends BaseRepository<Post> {
 *       public PostRepository() { super(Post.class); }
 *   }
 */
public abstract class BaseRepository<T extends Model> {

    protected final Class<T> modelClass;

    /**
     * Creates a new BaseRepository instance.
     *
     * @param modelClass The model class to instantiate
     */
    public BaseRepository(Class<T> modelClass) {
        this.modelClass = modelClass;
    }

    // ─── QUERY STARTER ───────────────────────────────────────

    /**
     * Start a new query builder for this model.
     */
    public ModelQueryBuilder<T> query() {
        return Model.query(modelClass);
    }

    // ─── FIND ────────────────────────────────────────────────

    /**
     * Find all records.
     */
    public List<T> findAll() {
        return Model.all(modelClass);
    }

    /**
     * Find by primary key.
     */
    public T findById(Object id) {
        return Model.find(modelClass, id);
    }

    /**
     * Find by primary key or throw.
     */
    public T findByIdOrFail(Object id) {
        return Model.findOrFail(modelClass, id);
    }

    /**
     * Find by a column value.
     */
    public T findBy(String column, Object value) {
        return query().where(column, value).first();
    }

    /**
     * Find all by a column value.
     */
    public List<T> findAllBy(String column, Object value) {
        return query().where(column, value).get();
    }

    /**
     * Find by multiple column values (AND).
     */
    public T findByAttributes(Map<String, Object> attributes) {
        ModelQueryBuilder<T> q = query();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            q.where(entry.getKey(), entry.getValue());
        }
        return q.first();
    }

    /**
     * Find multiple by IDs.
     */
    public List<T> findMany(List<Object> ids) {
        return query().whereIn("id", ids).get();
    }

    // ─── CREATE ──────────────────────────────────────────────

    /**
     * Create a new model with given attributes.
     */
    public T create(Map<String, Object> attributes) {
        return Model.create(modelClass, attributes);
    }

    /**
     * Find or create.
     */
    public T firstOrCreate(Map<String, Object> search, Map<String, Object> extra) {
        return Model.firstOrCreate(modelClass, search, extra);
    }

    /**
     * Finds a matching model or creates one if not found.
     *
     * @param search Attributes to search for
     * @return The model instance, or {@code null} if not found
     */
    public T firstOrCreate(Map<String, Object> search) {
        return firstOrCreate(search, Map.of());
    }

    // ─── UPDATE ──────────────────────────────────────────────

    /**
     * Update a model by ID.
     */
    public T update(Object id, Map<String, Object> attributes) {
        T model = findByIdOrFail(id);
        model.fill(attributes);
        model.save();
        return model;
    }

    /**
     * Update matching records (bulk).
     */
    public int updateWhere(String column, Object value, Map<String, Object> attributes) {
        return query().where(column, value).update(attributes);
    }

    // ─── DELETE ──────────────────────────────────────────────

    /**
     * Delete by primary key.
     */
    public boolean delete(Object id) {
        T model = findById(id);
        if (model == null) return false;
        return model.delete();
    }

    /**
     * Delete by primary keys.
     */
    public int destroy(Object... ids) {
        return Model.destroy(modelClass, ids);
    }

    /**
     * Delete matching records (bulk).
     */
    public int deleteWhere(String column, Object value) {
        return query().where(column, value).delete();
    }

    // ─── AGGREGATES ──────────────────────────────────────────

    /**
     * Returns the number of matching rows.
     *
     * @return The count or numeric result
     */
    public long count() {
        return query().count();
    }

    /**
     * Returns the number of rows matching a condition.
     *
     * @param column The column name
     * @param value The value to compare against
     * @return The count or numeric result
     */
    public long countWhere(String column, Object value) {
        return query().where(column, value).count();
    }

    /**
     * Checks if any rows match the query.
     *
     * @param id The primary key value
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean exists(Object id) {
        return query().where("id", id).exists();
    }

    /**
     * Checks if any record matches a column condition.
     *
     * @param column The column name
     * @param value The value to compare against
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean existsWhere(String column, Object value) {
        return query().where(column, value).exists();
    }

    /**
     * Returns the maximum value of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object max(String column) {
        return query().max(column);
    }

    /**
     * Returns the minimum value of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object min(String column) {
        return query().min(column);
    }

    /**
     * Returns the sum of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object sum(String column) {
        return query().sum(column);
    }

    /**
     * Returns the average of a column.
     *
     * @param column The column name
     * @return The result value, or {@code null} if not found
     */
    public Object avg(String column) {
        return query().avg(column);
    }

    // ─── PAGINATION ──────────────────────────────────────────

    /**
     * Paginate all records.
     */
    public Paginator<T> paginate(int page, int perPage) {
        return query().paginate(page, perPage);
    }

    /**
     * Paginates the query results.
     *
     * @param page Page number (starts at 1)
     * @return A paginated result set with metadata
     */
    public Paginator<T> paginate(int page) {
        return paginate(page, 15);
    }

    // ─── ORDERING ────────────────────────────────────────────

    /**
     * Orders by the given column descending (default: created_at).
     *
     * @return A list of results
     */
    public List<T> latest() {
        return query().latest().get();
    }

    /**
     * Orders by the given column descending (default: created_at).
     *
     * @param limit Maximum number of rows
     * @return A list of results
     */
    public List<T> latest(int limit) {
        return query().latest().limit(limit).get();
    }

    /**
     * Orders by the given column ascending (default: created_at).
     *
     * @return A list of results
     */
    public List<T> oldest() {
        return query().oldest().get();
    }

    /**
     * Executes the query and returns the first result, or null.
     *
     * @return The model instance, or {@code null} if not found
     */
    public T first() {
        return query().first();
    }

    // ─── PLUCK ───────────────────────────────────────────────

    /**
     * Extracts a single column value from each result.
     *
     * @param column The column name
     * @return A list of results
     */
    public List<Object> pluck(String column) {
        return query().pluck(column);
    }

    /**
     * Pluck Where.
     *
     * @param column The column name
     * @param whereCol The where col
     * @param whereVal The where val
     * @return A list of results
     */
    public List<Object> pluckWhere(String column, String whereCol, Object whereVal) {
        return query().where(whereCol, whereVal).pluck(column);
    }
}
