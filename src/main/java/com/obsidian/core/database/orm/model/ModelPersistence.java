package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.observer.ModelObserver;
import com.obsidian.core.database.orm.query.QueryBuilder;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Persistence logic: save, insert, update, delete, restore, refresh.
 * Observer callbacks pass Model — the concrete type known at runtime.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
abstract class ModelPersistence extends ModelAttributes
{
    boolean exists = false;

    /**
     * Implemented by Model — returns the concrete instance typed as Model.
     */
    abstract Model self();

    abstract ModelMetadata meta();

    /**
     * Persists the model — inserts if new, updates if existing.
     * Fires {@code saving}/{@code saved} observer callbacks and flushes the model cache on success.
     *
     * @return {@code true} if the operation succeeded, {@code false} if an observer vetoed it
     */
    public boolean save()
    {
        ModelObserver obs = meta().observer;
        if (obs != null && !obs.saving(self())) return false;
        boolean result = exists ? performUpdate() : performInsert();
        if (result) {
            if (obs != null) obs.saved(self());
            ModelCache.flush(self().getClass());
        }
        return result;
    }

    /**
     * Alias for {@link #save()}.
     *
     * @return {@code true} if the operation succeeded
     */
    public boolean saveIt() { return save(); }

    /**
     * Deletes the model. If soft deletes are enabled, sets {@code deleted_at} instead of
     * removing the row. Fires {@code deleting}/{@code deleted} observer callbacks and
     * flushes the model cache on success.
     *
     * @return {@code true} if deleted, {@code false} if the model does not exist in the database or an observer vetoed the operation
     */
    public boolean delete()
    {
        if (!exists) return false;
        ModelMetadata m = meta();
        ModelObserver obs = m.observer;
        if (obs != null && !obs.deleting(self())) return false;

        if (m.softDeletes) {
            _set("deleted_at", LocalDateTime.now());
            Map<String, Object> updateMap = new LinkedHashMap<>();
            updateMap.put("deleted_at", get("deleted_at"));
            new QueryBuilder(m.table)
                    .where(m.primaryKey, getId())
                    .update(updateMap);
            syncOriginal();
        } else {
            new QueryBuilder(m.table).where(m.primaryKey, getId()).delete();
            exists = false;
        }

        if (obs != null) obs.deleted(self());
        ModelCache.flush(self().getClass());
        return true;
    }

    /**
     * Restores a soft-deleted model by clearing {@code deleted_at}.
     * No-op if soft deletes are not enabled. Fires {@code restoring}/{@code restored} observer callbacks.
     *
     * @return {@code true} if restored, {@code false} if soft deletes are not enabled or an observer vetoed the operation
     */
    public boolean restore()
    {
        ModelMetadata m = meta();
        if (!m.softDeletes) return false;
        ModelObserver obs = m.observer;
        if (obs != null && !obs.restoring(self())) return false;

        _set("deleted_at", null);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("deleted_at", null);
        new QueryBuilder(m.table).where(m.primaryKey, getId()).update(update);
        syncOriginal();

        if (obs != null) obs.restored(self());
        return true;
    }

    /**
     * Permanently deletes the model, bypassing soft deletes.
     *
     * @return {@code true} always
     */
    public boolean forceDelete()
    {
        ModelMetadata m = meta();
        new QueryBuilder(m.table).where(m.primaryKey, getId()).delete();
        exists = false;
        return true;
    }

    /**
     * Reloads the model's attributes from the database.
     */
    void _refresh()
    {
        ModelMetadata m = meta();
        Map<String, Object> row = new QueryBuilder(m.table)
                .where(m.primaryKey, getId()).first();
        if (row != null) {
            attributes.clear();
            attributes.putAll(row);
            syncOriginal();
        }
    }

    /**
     * Returns {@code true} if this model instance exists in the database.
     *
     * @return {@code true} if persisted
     */
    public boolean exists() { return exists; }

    private boolean performInsert()
    {
        ModelMetadata m = meta();
        ModelObserver obs = m.observer;
        if (obs != null && !obs.creating(self())) return false;

        for (Map.Entry<String, Object> entry : m.defaults.entrySet())
            attributes.putIfAbsent(entry.getKey(), entry.getValue());

        if (m.timestamps) {
            LocalDateTime now = LocalDateTime.now();
            attributes.putIfAbsent("created_at", now);
            attributes.putIfAbsent("updated_at", now);
        }

        Map<String, Object> insertData = new LinkedHashMap<>(attributes);
        if (m.incrementing && insertData.get(m.primaryKey) == null)
            insertData.remove(m.primaryKey);

        Object generatedId = new QueryBuilder(m.table).insert(insertData);
        if (m.incrementing && generatedId != null)
            attributes.put(m.primaryKey, generatedId);

        exists = true;
        syncOriginal();
        if (obs != null) obs.created(self());
        return true;
    }

    private boolean performUpdate()
    {
        ModelMetadata m = meta();
        ModelObserver obs = m.observer;
        if (obs != null && !obs.updating(self())) return false;

        Map<String, Object> dirty = getDirty();
        if (dirty.isEmpty()) return true;

        if (m.timestamps) {
            dirty.put("updated_at", LocalDateTime.now());
            attributes.put("updated_at", dirty.get("updated_at"));
        }

        new QueryBuilder(m.table).where(m.primaryKey, getId()).update(dirty);
        syncOriginal();
        if (obs != null) obs.updated(self());
        return true;
    }
}