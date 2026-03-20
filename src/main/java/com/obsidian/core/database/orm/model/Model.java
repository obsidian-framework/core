package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.observer.ModelObserver;
import com.obsidian.core.database.orm.query.QueryBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Base Model class — ActiveRecord pattern.
 *
 * <p>Behaviour is split across a chain of abstract superclasses, each owning
 * one concern, so this file stays focused on the things that belong together:
 * instance state, the per-class metadata cache, configuration overrides, and
 * the static query/factory API.</p>
 *
 * <ul>
 *   <li>{@link ModelAttributes}  — get/set, type coercion, fill, dirty tracking</li>
 *   <li>{@link ModelPersistence} — save, delete, restore, refresh</li>
 *   <li>{@link ModelRelations}   — relation factories + loaded-relation cache</li>
 *   <li>{@link ModelSerializer}  — toMap, hydrate</li>
 *   <li>{@link Model}            — metadata cache, configuration, statics, utilities</li>
 * </ul>
 */
public abstract class Model extends ModelSerializer {

    // ─── Metadata cache (per-class, computed once) ───────────

    private static final Map<Class<? extends Model>, ModelMetadata> metadataCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    static ModelMetadata metadata(Class<? extends Model> modelClass) {
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

    // ─── SELF REFERENCE (used by superclasses for observer callbacks) ─────

    @Override
    Model self() { return this; }

    // ─── FLUENT PUBLIC API ───────────────────────────────────
    // Superclass mutating methods are package-private (_set, _fill, etc.).
    // These public methods delegate to them and return Model for chaining.

    public Model set(String key, Object value)      { _set(key, value);      return this; }
    public Model setRaw(String key, Object value)   { _setRaw(key, value);   return this; }
    public Model fill(Map<String, Object> attrs)    { _fill(attrs);          return this; }
    public Model forceFill(Map<String, Object> attrs){ _forceFill(attrs);    return this; }
    public Model refresh()                          { _refresh();            return this; }


    // ─── Configuration (override in subclass) ────────────────

    public String table() {
        Table annotation = getClass().getAnnotation(Table.class);
        if (annotation != null) return annotation.value();
        return getClass().getSimpleName().toLowerCase() + "s";
    }

    public String primaryKey()                              { return "id"; }
    protected boolean incrementing()                        { return true; }
    protected boolean timestamps()                          { return true; }
    protected boolean softDeletes()                         { return false; }
    protected List<String> hidden()                         { return Collections.emptyList(); }
    protected List<String> fillable()                       { return Collections.emptyList(); }
    protected List<String> guarded()                        { return Collections.singletonList("*"); }
    protected Map<String, Object> defaults()                { return Collections.emptyMap(); }
    protected List<Consumer<QueryBuilder>> globalScopes()   { return Collections.emptyList(); }
    @SuppressWarnings("rawtypes")
    protected ModelObserver observer()                      { return null; }
    protected Map<String, String> casts()                   { return Collections.emptyMap(); }

    // ─── STATIC QUERY STARTERS ───────────────────────────────

    public static <T extends Model> ModelQueryBuilder<T> query(Class<T> modelClass) {
        ModelMetadata meta = metadata(modelClass);
        return new ModelQueryBuilder<>(modelClass, meta.table, meta.globalScopes, meta.softDeletes);
    }

    public static <T extends Model> ModelQueryBuilder<T> where(Class<T> modelClass, String column, Object value) {
        return query(modelClass).where(column, value);
    }

    public static <T extends Model> ModelQueryBuilder<T> where(Class<T> modelClass, String column, String op, Object value) {
        return query(modelClass).where(column, op, value);
    }

    // ─── STATIC FINDERS ──────────────────────────────────────

    public static <T extends Model> T find(Class<T> modelClass, Object id) {
        return query(modelClass).where(metadata(modelClass).primaryKey, id).first();
    }

    public static <T extends Model> T findOrFail(Class<T> modelClass, Object id) {
        T model = find(modelClass, id);
        if (model == null) throw new ModelNotFoundException(modelClass.getSimpleName() + " not found with id: " + id);
        return model;
    }

    public static <T extends Model> List<T> all(Class<T> modelClass) {
        return query(modelClass).get();
    }

    // ─── STATIC WRITE HELPERS ────────────────────────────────

    public static <T extends Model> T create(Class<T> modelClass, Map<String, Object> attributes) {
        T model = newInstance(modelClass);
        model.fill(attributes);
        model.save();
        return model;
    }

    public static <T extends Model> T firstOrCreate(Class<T> modelClass,
                                                     Map<String, Object> search,
                                                     Map<String, Object> extra) {
        ModelQueryBuilder<T> q = query(modelClass);
        search.forEach((k, v) -> q.where(k, v));
        T found = q.first();
        if (found != null) return found;
        Map<String, Object> merged = new LinkedHashMap<>(search);
        merged.putAll(extra);
        return create(modelClass, merged);
    }

    public static <T extends Model> int destroy(Class<T> modelClass, Object... ids) {
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

    // ─── UTILITIES ───────────────────────────────────────────

    // ─── UTILITIES ───────────────────────────────────────────

        @SuppressWarnings("unchecked")
    public static <T extends Model> T newInstance(Class<T> modelClass) {
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
    public boolean equals(Object o) {
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
