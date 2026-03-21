package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.observer.ModelObserver;
import com.obsidian.core.database.orm.query.QueryBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Base Model class — ActiveRecord pattern.
 */
public abstract class Model extends ModelSerializer
{
    private static final Map<Class<? extends Model>, ModelMetadata> metadataCache = new java.util.concurrent.ConcurrentHashMap<>();

    static ModelMetadata metadata(Class<? extends Model> modelClass)
    {
        return metadataCache.computeIfAbsent(modelClass, cls -> {
            java.lang.reflect.Constructor<? extends Model> ctor;
            try {
                ctor = cls.getDeclaredConstructor();
                ctor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Model must have a no-arg constructor: " + cls.getSimpleName(), e);
            }
            Model instance;
            try {
                instance = ctor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate model: " + cls.getSimpleName(), e);
            }
            return new ModelMetadata(
                    instance.table(), instance.primaryKey(), instance.incrementing(),
                    instance.timestamps(), instance.softDeletes(), instance.hidden(),
                    instance.fillable(), instance.guarded(), instance.defaults(),
                    instance.globalScopes(), instance.observer(), instance.casts(), ctor
            );
        });
    }

    @Override
    ModelMetadata meta() { return metadata(getClass()); }

    @Override
    Model self() { return this; }

    /**
     * Sets a single attribute value.
     *
     * @param key   attribute name
     * @param value value to assign
     * @return this model for chaining
     */
    public Model set(String key, Object value) { _set(key, value); return this; }

    /**
     * Sets a raw attribute value, bypassing cast handling.
     *
     * @param key   attribute name
     * @param value raw value to assign
     * @return this model for chaining
     */
    public Model setRaw(String key, Object value) { _setRaw(key, value); return this; }

    /**
     * Mass-assigns attributes, respecting {@link #fillable()} and {@link #guarded()} rules.
     *
     * @param attrs attribute map
     * @return this model for chaining
     */
    public Model fill(Map<String, Object> attrs) { _fill(attrs); return this; }

    /**
     * Mass-assigns attributes, bypassing {@link #fillable()} and {@link #guarded()} rules.
     *
     * @param attrs attribute map
     * @return this model for chaining
     */
    public Model forceFill(Map<String, Object> attrs) { _forceFill(attrs); return this; }

    /**
     * Reloads the model's attributes from the database.
     *
     * @return this model for chaining
     */
    public Model refresh() { _refresh(); return this; }

    /**
     * Returns the database table name for this model.
     *
     * @return table name derived from the {@link Table} annotation or the class name
     */
    public String table()
    {
        Table annotation = getClass().getAnnotation(Table.class);
        if (annotation != null) return annotation.value();
        return getClass().getSimpleName().toLowerCase() + "s";
    }

    /**
     * Returns the primary key column name.
     *
     * @return primary key column, defaults to {@code "id"}
     */
    public String primaryKey() { return "id"; }

    /**
     * Whether the primary key auto-increments.
     *
     * @return {@code true} by default
     */
    protected boolean incrementing() { return true; }

    /**
     * Whether {@code created_at} and {@code updated_at} are auto-managed.
     *
     * @return {@code true} by default
     */
    protected boolean timestamps() { return true; }

    /**
     * Whether soft deletes via {@code deleted_at} are enabled.
     *
     * @return {@code false} by default
     */
    protected boolean softDeletes() { return false; }

    /**
     * Attributes excluded from {@link #toMap()} serialization.
     *
     * @return empty list by default
     */
    protected List<String> hidden() { return Collections.emptyList(); }

    /**
     * Attributes allowed for mass-assignment via {@link #fill}.
     *
     * @return empty list by default, allowing all unless guarded
     */
    protected List<String> fillable() { return Collections.emptyList(); }

    /**
     * Attributes blocked from mass-assignment.
     *
     * @return list containing {@code "*"} by default, blocking all
     */
    protected List<String> guarded() { return Collections.singletonList("*"); }

    /**
     * Default attribute values applied on insert.
     *
     * @return empty map by default
     */
    protected Map<String, Object> defaults() { return Collections.emptyMap(); }

    /**
     * Global query scopes applied to every query for this model.
     *
     * @return empty list by default
     */
    protected List<Consumer<QueryBuilder>> globalScopes() { return Collections.emptyList(); }

    /**
     * Lifecycle observer for this model.
     *
     * @return {@code null} by default, meaning no callbacks
     */
    @SuppressWarnings("rawtypes")
    protected ModelObserver observer() { return null; }

    /**
     * Attribute type casts mapping column names to type strings.
     *
     * @return empty map by default
     */
    protected Map<String, String> casts() { return Collections.emptyMap(); }

    /**
     * Creates a new query builder for the given model class.
     *
     * @param modelClass model class
     * @return a fresh {@link ModelQueryBuilder}
     */
    public static <T extends Model> ModelQueryBuilder<T> query(Class<T> modelClass)
    {
        ModelMetadata meta = metadata(modelClass);
        return new ModelQueryBuilder<>(modelClass, meta.table, meta.globalScopes, meta.softDeletes);
    }

    /**
     * Creates a query builder with an initial {@code WHERE column = value} clause.
     *
     * @param modelClass model class
     * @param column     column name
     * @param value      value to match
     * @return a {@link ModelQueryBuilder} with the condition applied
     */
    public static <T extends Model> ModelQueryBuilder<T> where(Class<T> modelClass, String column, Object value) {
        return query(modelClass).where(column, value);
    }

    /**
     * Creates a query builder with an initial {@code WHERE column op value} clause.
     *
     * @param modelClass model class
     * @param column     column name
     * @param op         comparison operator
     * @param value      value to compare
     * @return a {@link ModelQueryBuilder} with the condition applied
     */
    public static <T extends Model> ModelQueryBuilder<T> where(Class<T> modelClass, String column, String op, Object value) {
        return query(modelClass).where(column, op, value);
    }

    /**
     * Finds a model by primary key, or returns {@code null} if not found.
     *
     * @param modelClass model class
     * @param id         primary key value
     * @return the model instance, or {@code null}
     */
    public static <T extends Model> T find(Class<T> modelClass, Object id)
    {
        if (id == null) return null;
        if (!ModelCache.isEnabled(modelClass)) return query(modelClass).where(metadata(modelClass).primaryKey, id).first();

        String key = ModelCache.pkKey(modelClass, id);
        return ModelCache.remember(modelClass, key, () -> query(modelClass).where(metadata(modelClass).primaryKey, id).first());
    }

    /**
     * Finds a model by primary key, throwing {@link ModelNotFoundException} if not found.
     *
     * @param modelClass model class
     * @param id         primary key value
     * @return the model instance
     * @throws ModelNotFoundException if no record exists for the given id
     */
    public static <T extends Model> T findOrFail(Class<T> modelClass, Object id)
    {
        T model = find(modelClass, id);
        if (model == null) throw new ModelNotFoundException(modelClass.getSimpleName() + " not found with id: " + id);
        return model;
    }

    /**
     * Returns all rows for the given model class.
     *
     * @param modelClass model class
     * @return list of all model instances
     */
    public static <T extends Model> List<T> all(Class<T> modelClass) {
        return query(modelClass).get();
    }

    /**
     * Creates and persists a new model instance with the given attributes.
     *
     * @param modelClass model class
     * @param attributes attribute map, subject to {@link #fillable()} and {@link #guarded()} rules
     * @return the saved model instance
     */
    public static <T extends Model> T create(Class<T> modelClass, Map<String, Object> attributes)
    {
        T model = newInstance(modelClass);
        model.fill(attributes);
        model.save();
        return model;
    }

    /**
     * Finds the first record matching {@code search}, or creates one if none exists.
     *
     * @param modelClass model class
     * @param search     attributes used to locate the existing record
     * @param extra      additional attributes merged in on creation only
     * @return the existing or newly created model instance
     */
    public static <T extends Model> T firstOrCreate(Class<T> modelClass, Map<String, Object> search, Map<String, Object> extra)
    {
        ModelQueryBuilder<T> q = query(modelClass);
        search.forEach((k, v) -> q.where(k, v));
        T found = q.first();
        if (found != null) return found;
        Map<String, Object> merged = new LinkedHashMap<>(search);
        merged.putAll(extra);
        return create(modelClass, merged);
    }

    /**
     * Deletes (or soft-deletes) multiple records by primary key in a single query.
     *
     * @param modelClass model class
     * @param ids        primary key values to delete
     * @return number of affected rows
     */
    public static <T extends Model> int destroy(Class<T> modelClass, Object... ids)
    {
        if (ids.length == 0) return 0;
        ModelMetadata meta = metadata(modelClass);
        if (meta.softDeletes) {
            return new QueryBuilder(meta.table)
                    .whereIn(meta.primaryKey, Arrays.asList(ids))
                    .update(Map.of("deleted_at", LocalDateTime.now()));
        } else {
            return new QueryBuilder(meta.table)
                    .whereIn(meta.primaryKey, Arrays.asList(ids))
                    .delete();
        }
    }

    /**
     * Instantiates a new model of the given class without persisting it.
     *
     * @param modelClass model class
     * @return a new, unsaved model instance
     */
    @SuppressWarnings("unchecked")
    public static <T extends Model> T newInstance(Class<T> modelClass)
    {
        ModelMetadata cached = metadataCache.get(modelClass);
        try {
            if (cached != null && cached.constructor != null) {
                return (T) cached.constructor.newInstance();
            }
            java.lang.reflect.Constructor<T> ctor = modelClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate model: " + modelClass.getSimpleName(), e);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Model model = (Model) o;
        return Objects.equals(getId(), model.getId()) && Objects.equals(table(), model.table());
    }

    @Override
    public int hashCode() { return Objects.hash(table(), getId()); }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + getId() + ", attributes=" + getAttributes() + ")";
    }
}