package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.observer.ModelObserver;
import com.obsidian.core.database.orm.query.QueryBuilder;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable metadata cache for a Model class.
 */
final class ModelMetadata
{

    /** Database table name (from @Table annotation or convention). */
    final String table;

    /** Primary key column name (default: "id"). */
    final String primaryKey;

    /** Whether the primary key auto-increments. */
    final boolean incrementing;

    /** Whether created_at/updated_at are auto-managed. */
    final boolean timestamps;

    /** Whether soft deletes (deleted_at) are enabled. */
    final boolean softDeletes;

    /** Attributes excluded from toMap() serialization. */
    final List<String> hidden;

    /** Attributes allowed for mass-assignment via fill(). */
    final List<String> fillable;

    /** Attributes blocked from mass-assignment. */
    final List<String> guarded;

    /** Default attribute values applied on insert. */
    final Map<String, Object> defaults;

    /** Global query scopes applied to every query. */
    final List<Consumer<QueryBuilder>> globalScopes;

    /** Lifecycle observer (creating/updating/deleting callbacks). May be null. */
    @SuppressWarnings("rawtypes")
    final ModelObserver observer;

    /** Attribute type casts (column -> type string). */
    final Map<String, String> casts;

    /**
     * Cached no-arg constructor for this model class.
     * Populated once during metadata creation; reused on every {@code newInstance()} call.
     * Eliminates the per-call {@code getDeclaredConstructor()} reflection lookup during hydration.
     */
    @SuppressWarnings("rawtypes")
    final Constructor constructor;

    /**
     * Creates an immutable metadata snapshot.
     *
     * @param table        Database table name
     * @param primaryKey   Primary key column
     * @param incrementing Whether PK auto-increments
     * @param timestamps   Whether timestamps are auto-managed
     * @param softDeletes  Whether soft deletes are enabled
     * @param hidden       Hidden attributes list
     * @param fillable     Fillable attributes list
     * @param guarded      Guarded attributes list
     * @param defaults     Default attribute values
     * @param globalScopes Global query scopes
     * @param observer     Lifecycle observer (may be null)
     * @param casts        Attribute type casts
     */
    @SuppressWarnings("rawtypes")
    ModelMetadata(String table, String primaryKey, boolean incrementing, boolean timestamps,
                  boolean softDeletes, List<String> hidden, List<String> fillable,
                  List<String> guarded, Map<String, Object> defaults,
                  List<Consumer<QueryBuilder>> globalScopes, ModelObserver observer,
                  Map<String, String> casts, Constructor constructor
    ) {
        this.table = table;
        this.primaryKey = primaryKey;
        this.incrementing = incrementing;
        this.timestamps = timestamps;
        this.softDeletes = softDeletes;
        this.hidden = Collections.unmodifiableList(hidden);
        this.fillable = Collections.unmodifiableList(fillable);
        this.guarded = Collections.unmodifiableList(guarded);
        this.defaults = Collections.unmodifiableMap(defaults);
        this.globalScopes = Collections.unmodifiableList(globalScopes);
        this.observer = observer;
        this.casts = Collections.unmodifiableMap(casts);
        this.constructor = constructor;
    }
}