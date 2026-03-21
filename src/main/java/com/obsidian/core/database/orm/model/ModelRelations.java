package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.relation.*;

import java.util.*;

/**
 * Relation factory methods and the loaded-relation cache.
 */
abstract class ModelRelations extends ModelPersistence
{
    private final Map<String, List<? extends Model>> loadedRelations = new LinkedHashMap<>();

    abstract String primaryKey();

    /**
     * Creates a has-one relation to the given model using a custom foreign key.
     *
     * @param related     related model class
     * @param foreignKey  foreign key on the related table
     * @return configured {@link HasOne} relation
     */
    protected <T extends Model> HasOne<T> hasOne(Class<T> related, String foreignKey) {
        return new HasOne<>(self(), related, foreignKey, primaryKey());
    }

    /**
     * Creates a has-one relation to the given model using the default foreign key.
     *
     * @param related related model class
     * @return configured {@link HasOne} relation
     */
    protected <T extends Model> HasOne<T> hasOne(Class<T> related) {
        return hasOne(related, getClass().getSimpleName().toLowerCase() + "_id");
    }

    /**
     * Creates a has-many relation to the given model using a custom foreign key.
     *
     * @param related    related model class
     * @param foreignKey foreign key on the related table
     * @return configured {@link HasMany} relation
     */
    protected <T extends Model> HasMany<T> hasMany(Class<T> related, String foreignKey) {
        return new HasMany<>(self(), related, foreignKey, primaryKey());
    }

    /**
     * Creates a has-many relation to the given model using the default foreign key.
     *
     * @param related related model class
     * @return configured {@link HasMany} relation
     */
    protected <T extends Model> HasMany<T> hasMany(Class<T> related) {
        return hasMany(related, getClass().getSimpleName().toLowerCase() + "_id");
    }

    /**
     * Creates a belongs-to relation to the given model using a custom foreign key.
     *
     * @param related    related model class
     * @param foreignKey foreign key on this model's table
     * @return configured {@link BelongsTo} relation
     */
    protected <T extends Model> BelongsTo<T> belongsTo(Class<T> related, String foreignKey)
    {
        T instance = Model.newInstance(related);
        return new BelongsTo<>(self(), related, foreignKey, instance.primaryKey());
    }

    /**
     * Creates a belongs-to relation to the given model using the default foreign key.
     *
     * @param related related model class
     * @return configured {@link BelongsTo} relation
     */
    protected <T extends Model> BelongsTo<T> belongsTo(Class<T> related) {
        return belongsTo(related, related.getSimpleName().toLowerCase() + "_id");
    }

    /**
     * Creates a belongs-to-many relation using a custom pivot table and keys.
     *
     * @param related          related model class
     * @param pivotTable       pivot table name
     * @param foreignPivotKey  foreign key for this model in the pivot table
     * @param relatedPivotKey  foreign key for the related model in the pivot table
     * @return configured {@link BelongsToMany} relation
     */
    protected <T extends Model> BelongsToMany<T> belongsToMany(Class<T> related, String pivotTable, String foreignPivotKey, String relatedPivotKey) {
        return new BelongsToMany<>(self(), related, pivotTable, foreignPivotKey, relatedPivotKey);
    }

    /**
     * Creates a belongs-to-many relation using a custom pivot table and default keys.
     *
     * @param related    related model class
     * @param pivotTable pivot table name
     * @return configured {@link BelongsToMany} relation
     */
    protected <T extends Model> BelongsToMany<T> belongsToMany(Class<T> related, String pivotTable)
    {
        String fk = getClass().getSimpleName().toLowerCase() + "_id";
        String rk = related.getSimpleName().toLowerCase() + "_id";
        return belongsToMany(related, pivotTable, fk, rk);
    }

    /**
     * Creates a has-many-through relation using explicit keys.
     *
     * @param related         final related model class
     * @param through         intermediate model class
     * @param firstKey        foreign key on the through model pointing to this model
     * @param secondKey       foreign key on the related model pointing to the through model
     * @param localKey        local key on this model
     * @param secondLocalKey  local key on the through model
     * @return configured {@link HasManyThrough} relation
     */
    protected <T extends Model> HasManyThrough<T> hasManyThrough(Class<T> related, Class<? extends Model> through, String firstKey, String secondKey, String localKey, String secondLocalKey) {
        return new HasManyThrough<>(self(), related, through, firstKey, secondKey, localKey, secondLocalKey);
    }

    /**
     * Creates a has-many-through relation using default keys.
     *
     * @param related  final related model class
     * @param through  intermediate model class
     * @return configured {@link HasManyThrough} relation
     */
    protected <T extends Model> HasManyThrough<T> hasManyThrough(Class<T> related, Class<? extends Model> through)
    {
        String firstKey  = getClass().getSimpleName().toLowerCase() + "_id";
        String secondKey = through.getSimpleName().toLowerCase() + "_id";
        return hasManyThrough(related, through, firstKey, secondKey, "id", "id");
    }

    /**
     * Creates a morph-one relation.
     *
     * @param related    related model class
     * @param morphName  morph type/id column prefix
     * @return configured {@link MorphOne} relation
     */
    protected <T extends Model> MorphOne<T> morphOne(Class<T> related, String morphName) {
        return new MorphOne<>(self(), related, morphName, primaryKey());
    }

    /**
     * Creates a morph-many relation.
     *
     * @param related    related model class
     * @param morphName  morph type/id column prefix
     * @return configured {@link MorphMany} relation
     */
    protected <T extends Model> MorphMany<T> morphMany(Class<T> related, String morphName) {
        return new MorphMany<>(self(), related, morphName, primaryKey());
    }

    /**
     * Creates a morph-to relation.
     *
     * @param morphName  morph type/id column prefix
     * @param morphMap   map of morph type strings to model classes
     * @return configured {@link MorphTo} relation
     */
    protected <T extends Model> MorphTo<T> morphTo(String morphName, Map<String, Class<? extends Model>> morphMap) {
        return new MorphTo<>(self(), morphName, morphMap);
    }

    /**
     * Returns the loaded models for the given relation name, or {@code null} if not loaded.
     *
     * @param name relation name
     * @return list of related model instances, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T extends Model> List<T> getRelation(String name) {
        return (List<T>) loadedRelations.get(name);
    }

    /**
     * Stores the loaded models for the given relation name.
     *
     * @param name   relation name
     * @param models list of related model instances
     */
    public void setRelation(String name, List<? extends Model> models) {
        loadedRelations.put(name, models);
    }

    /**
     * Returns {@code true} if the given relation has already been loaded.
     *
     * @param name relation name
     * @return {@code true} if the relation is cached
     */
    public boolean relationLoaded(String name) {
        return loadedRelations.containsKey(name);
    }

    /**
     * Returns the raw loaded-relations map.
     *
     * @return map of relation name to loaded model instances
     */
    Map<String, List<? extends Model>> getLoadedRelations() {
        return loadedRelations;
    }
}