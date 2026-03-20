package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.relation.*;

import java.util.*;

/**
 * Relation factory methods and the loaded-relation cache.
 */
abstract class ModelRelations extends ModelPersistence {

    private final Map<String, List<? extends Model>> loadedRelations = new LinkedHashMap<>();

    abstract String primaryKey();

    // ─── RELATION FACTORIES ──────────────────────────────────

    protected <T extends Model> HasOne<T> hasOne(Class<T> related, String foreignKey) {
        return new HasOne<>(self(), related, foreignKey, primaryKey());
    }

    protected <T extends Model> HasOne<T> hasOne(Class<T> related) {
        return hasOne(related, getClass().getSimpleName().toLowerCase() + "_id");
    }

    protected <T extends Model> HasMany<T> hasMany(Class<T> related, String foreignKey) {
        return new HasMany<>(self(), related, foreignKey, primaryKey());
    }

    protected <T extends Model> HasMany<T> hasMany(Class<T> related) {
        return hasMany(related, getClass().getSimpleName().toLowerCase() + "_id");
    }

    protected <T extends Model> BelongsTo<T> belongsTo(Class<T> related, String foreignKey) {
        T instance = Model.newInstance(related);
        return new BelongsTo<>(self(), related, foreignKey, instance.primaryKey());
    }

    protected <T extends Model> BelongsTo<T> belongsTo(Class<T> related) {
        return belongsTo(related, related.getSimpleName().toLowerCase() + "_id");
    }

    protected <T extends Model> BelongsToMany<T> belongsToMany(Class<T> related, String pivotTable,
                                                               String foreignPivotKey, String relatedPivotKey) {
        return new BelongsToMany<>(self(), related, pivotTable, foreignPivotKey, relatedPivotKey);
    }

    protected <T extends Model> BelongsToMany<T> belongsToMany(Class<T> related, String pivotTable) {
        String fk = getClass().getSimpleName().toLowerCase() + "_id";
        String rk = related.getSimpleName().toLowerCase() + "_id";
        return belongsToMany(related, pivotTable, fk, rk);
    }

    protected <T extends Model> HasManyThrough<T> hasManyThrough(
            Class<T> related, Class<? extends Model> through,
            String firstKey, String secondKey, String localKey, String secondLocalKey) {
        return new HasManyThrough<>(self(), related, through, firstKey, secondKey, localKey, secondLocalKey);
    }

    protected <T extends Model> HasManyThrough<T> hasManyThrough(
            Class<T> related, Class<? extends Model> through) {
        String firstKey  = getClass().getSimpleName().toLowerCase() + "_id";
        String secondKey = through.getSimpleName().toLowerCase() + "_id";
        return hasManyThrough(related, through, firstKey, secondKey, "id", "id");
    }

    protected <T extends Model> MorphOne<T> morphOne(Class<T> related, String morphName) {
        return new MorphOne<>(self(), related, morphName, primaryKey());
    }

    protected <T extends Model> MorphMany<T> morphMany(Class<T> related, String morphName) {
        return new MorphMany<>(self(), related, morphName, primaryKey());
    }

    protected <T extends Model> MorphTo<T> morphTo(String morphName, Map<String, Class<? extends Model>> morphMap) {
        return new MorphTo<>(self(), morphName, morphMap);
    }

    // ─── LOADED RELATIONS CACHE ──────────────────────────────

    @SuppressWarnings("unchecked")
    public <T extends Model> List<T> getRelation(String name) {
        return (List<T>) loadedRelations.get(name);
    }

    public void setRelation(String name, List<? extends Model> models) {
        loadedRelations.put(name, models);
    }

    public boolean relationLoaded(String name) {
        return loadedRelations.containsKey(name);
    }

    Map<String, List<? extends Model>> getLoadedRelations() {
        return loadedRelations;
    }
}
