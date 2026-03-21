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
public abstract class BaseRepository<T extends Model>
{
    protected final Class<T> modelClass;

    /**
     * Creates a new repository for the given model class.
     *
     * @param modelClass model class to operate on
     */
    public BaseRepository(Class<T> modelClass) {
        this.modelClass = modelClass;
    }

    /**
     * Returns a new query builder for this model.
     *
     * @return fresh {@link ModelQueryBuilder}
     */
    public ModelQueryBuilder<T> query() {
        return Model.query(modelClass);
    }

    /**
     * Returns all records.
     *
     * @return list of all model instances
     */
    public List<T> findAll() {
        return Model.all(modelClass);
    }

    /**
     * Finds a record by primary key, or returns {@code null} if not found.
     *
     * @param id primary key value
     * @return model instance, or {@code null}
     */
    public T findById(Object id) {
        return Model.find(modelClass, id);
    }

    /**
     * Finds a record by primary key, throwing if not found.
     *
     * @param id primary key value
     * @return model instance
     * @throws com.obsidian.core.database.orm.model.ModelNotFoundException if no record exists
     */
    public T findByIdOrFail(Object id) {
        return Model.findOrFail(modelClass, id);
    }

    /**
     * Finds the first record where the given column equals the given value.
     *
     * @param column column name
     * @param value  value to match
     * @return first matching model instance, or {@code null}
     */
    public T findBy(String column, Object value) {
        return query().where(column, value).first();
    }

    /**
     * Returns all records where the given column equals the given value.
     *
     * @param column column name
     * @param value  value to match
     * @return list of matching model instances
     */
    public List<T> findAllBy(String column, Object value) {
        return query().where(column, value).get();
    }

    /**
     * Finds the first record matching all given attributes (AND conditions).
     *
     * @param attributes column-to-value map of conditions
     * @return first matching model instance, or {@code null}
     */
    public T findByAttributes(Map<String, Object> attributes)
    {
        ModelQueryBuilder<T> q = query();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            q.where(entry.getKey(), entry.getValue());
        }
        return q.first();
    }

    /**
     * Returns all records whose primary key is in the given list.
     *
     * @param ids list of primary key values
     * @return list of matching model instances
     */
    public List<T> findMany(List<Object> ids) {
        return query().whereIn("id", ids).get();
    }

    /**
     * Creates and persists a new model with the given attributes.
     *
     * @param attributes column-to-value map
     * @return saved model instance
     */
    public T create(Map<String, Object> attributes) {
        return Model.create(modelClass, attributes);
    }

    /**
     * Finds the first record matching {@code search}, or creates one merging
     * {@code search} and {@code extra} if none exists.
     *
     * @param search attributes used to locate the existing record
     * @param extra  additional attributes merged in on creation only
     * @return existing or newly created model instance
     */
    public T firstOrCreate(Map<String, Object> search, Map<String, Object> extra) {
        return Model.firstOrCreate(modelClass, search, extra);
    }

    /**
     * Finds the first record matching {@code search}, or creates one if none exists.
     *
     * @param search attributes used to locate the existing record
     * @return existing or newly created model instance
     */
    public T firstOrCreate(Map<String, Object> search) {
        return firstOrCreate(search, Map.of());
    }

    /**
     * Finds a record by primary key, applies the given attributes, and saves it.
     *
     * @param id         primary key value
     * @param attributes column-to-value map to apply
     * @return updated model instance
     */
    public T update(Object id, Map<String, Object> attributes)
    {
        T model = findByIdOrFail(id);
        model.fill(attributes);
        model.save();
        return model;
    }

    /**
     * Bulk-updates all records where the given column equals the given value.
     *
     * @param column     column name to filter on
     * @param value      value to match
     * @param attributes column-to-value map to apply
     * @return number of affected rows
     */
    public int updateWhere(String column, Object value, Map<String, Object> attributes) {
        return query().where(column, value).update(attributes);
    }

    /**
     * Deletes the record with the given primary key.
     *
     * @param id primary key value
     * @return {@code true} if deleted, {@code false} if not found
     */
    public boolean delete(Object id)
    {
        T model = findById(id);
        if (model == null) return false;
        return model.delete();
    }

    /**
     * Deletes multiple records by primary key in a single query.
     *
     * @param ids primary key values to delete
     * @return number of affected rows
     */
    public int destroy(Object... ids) {
        return Model.destroy(modelClass, ids);
    }

    /**
     * Bulk-deletes all records where the given column equals the given value.
     *
     * @param column column name
     * @param value  value to match
     * @return number of affected rows
     */
    public int deleteWhere(String column, Object value) {
        return query().where(column, value).delete();
    }

    /**
     * Returns the total number of records.
     *
     * @return record count
     */
    public long count() {
        return query().count();
    }

    /**
     * Returns the number of records where the given column equals the given value.
     *
     * @param column column name
     * @param value  value to match
     * @return record count
     */
    public long countWhere(String column, Object value) {
        return query().where(column, value).count();
    }

    /**
     * Returns {@code true} if a record with the given primary key exists.
     *
     * @param id primary key value
     * @return {@code true} if found
     */
    public boolean exists(Object id) {
        return query().where("id", id).exists();
    }

    /**
     * Returns {@code true} if any record matches the given column condition.
     *
     * @param column column name
     * @param value  value to match
     * @return {@code true} if at least one match exists
     */
    public boolean existsWhere(String column, Object value) {
        return query().where(column, value).exists();
    }

    /**
     * Returns the maximum value of the given column.
     *
     * @param column column name
     * @return maximum value, or {@code null}
     */
    public Object max(String column) {
        return query().max(column);
    }

    /**
     * Returns the minimum value of the given column.
     *
     * @param column column name
     * @return minimum value, or {@code null}
     */
    public Object min(String column) {
        return query().min(column);
    }

    /**
     * Returns the sum of the given column.
     *
     * @param column column name
     * @return sum value, or {@code null}
     */
    public Object sum(String column) {
        return query().sum(column);
    }

    /**
     * Returns the average of the given column.
     *
     * @param column column name
     * @return average value, or {@code null}
     */
    public Object avg(String column) {
        return query().avg(column);
    }

    /**
     * Paginates all records.
     *
     * @param page    page number, starting at 1
     * @param perPage number of items per page
     * @return paginated result set
     */
    public Paginator<T> paginate(int page, int perPage) {
        return query().paginate(page, perPage);
    }

    /**
     * Paginates all records with a default page size of 15.
     *
     * @param page page number, starting at 1
     * @return paginated result set
     */
    public Paginator<T> paginate(int page) {
        return paginate(page, 15);
    }

    /**
     * Returns all records ordered by {@code created_at} descending.
     *
     * @return list of model instances
     */
    public List<T> latest() {
        return query().latest().get();
    }

    /**
     * Returns the most recent records ordered by {@code created_at} descending.
     *
     * @param limit maximum number of records to return
     * @return list of model instances
     */
    public List<T> latest(int limit) {
        return query().latest().limit(limit).get();
    }

    /**
     * Returns all records ordered by {@code created_at} ascending.
     *
     * @return list of model instances
     */
    public List<T> oldest() {
        return query().oldest().get();
    }

    /**
     * Returns the first record, or {@code null} if none exists.
     *
     * @return first model instance, or {@code null}
     */
    public T first() {
        return query().first();
    }

    /**
     * Returns the value of the given column from each record.
     *
     * @param column column name to extract
     * @return list of column values
     */
    public List<Object> pluck(String column) {
        return query().pluck(column);
    }

    /**
     * Returns the value of the given column from records matching a condition.
     *
     * @param column   column name to extract
     * @param whereCol column name to filter on
     * @param whereVal value to match
     * @return list of column values
     */
    public List<Object> pluckWhere(String column, String whereCol, Object whereVal) {
        return query().where(whereCol, whereVal).pluck(column);
    }
}