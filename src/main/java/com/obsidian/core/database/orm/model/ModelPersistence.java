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
abstract class ModelPersistence extends ModelAttributes {

    boolean exists = false;

    /** Implemented by Model — returns the concrete instance typed as Model. */
    abstract Model self();

    abstract ModelMetadata meta();

    // ─── PUBLIC API ──────────────────────────────────────────

    public boolean save() {
        ModelObserver obs = meta().observer;
        if (obs != null && !obs.saving(self())) return false;
        boolean result = exists ? performUpdate() : performInsert();
        if (result && obs != null) obs.saved(self());
        return result;
    }

    public boolean saveIt() { return save(); }

    public boolean delete() {
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
        return true;
    }

    public boolean restore() {
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

    public boolean forceDelete() {
        ModelMetadata m = meta();
        new QueryBuilder(m.table).where(m.primaryKey, getId()).delete();
        exists = false;
        return true;
    }

    void _refresh() {
        ModelMetadata m = meta();
        Map<String, Object> row = new QueryBuilder(m.table)
                .where(m.primaryKey, getId()).first();
        if (row != null) {
            attributes.clear();
            attributes.putAll(row);
            syncOriginal();
        }
    }

    public boolean exists() { return exists; }

    // ─── INTERNAL ────────────────────────────────────────────

    private boolean performInsert() {
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

    private boolean performUpdate() {
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