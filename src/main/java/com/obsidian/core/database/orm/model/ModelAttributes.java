package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.cast.AttributeCaster;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Attribute access, type coercion, mass-assignment, and dirty tracking.
 * Package-private and abstract — only {@link Model} is public.
 * Fluent methods return void here; Model redeclares them returning Model.
 */
abstract class ModelAttributes {

    final Map<String, Object> attributes = new LinkedHashMap<>();
    final Map<String, Object> original   = new LinkedHashMap<>();

    abstract ModelMetadata meta();

    // ─── GET ─────────────────────────────────────────────────

    public Object get(String key) {
        Object value = attributes.get(key);
        String castType = meta().casts.get(key);
        if (castType != null && value != null) return AttributeCaster.castGet(value, castType);
        return value;
    }

    public Object getRaw(String key) { return attributes.get(key); }

    public String getString(String key) {
        Object val = get(key); return val != null ? val.toString() : null;
    }

    public Integer getInteger(String key) {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    public Long getLong(String key) {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    public Double getDouble(String key) {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(val.toString());
    }

    public Boolean getBoolean(String key) {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return Boolean.parseBoolean(val.toString());
    }

    public LocalDateTime getDateTime(String key) {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof LocalDateTime) return (LocalDateTime) val;
        if (val instanceof java.sql.Timestamp) return ((java.sql.Timestamp) val).toLocalDateTime();
        return LocalDateTime.parse(val.toString());
    }

    // ─── SET ─────────────────────────────────────────────────
    // Return void — Model redeclares these returning Model for fluent chaining.
    // Java allows a subclass to redeclare (hide) a void method with a covariant
    // return type when it's not an @Override of the same signature.

    void _set(String key, Object value) {
        String castType = meta().casts.get(key);
        if (castType != null && value != null) value = AttributeCaster.castSet(value, castType);
        attributes.put(key, value);
    }

    void _setRaw(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getId() { return attributes.get(meta().primaryKey); }

    public Map<String, Object> getAttributes() { return Collections.unmodifiableMap(attributes); }

    // ─── MASS ASSIGNMENT ─────────────────────────────────────

    void _fill(Map<String, Object> attrs) {
        List<String> fillable = meta().fillable;
        List<String> guarded  = meta().guarded;
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            if (isFillable(entry.getKey(), fillable, guarded))
                _set(entry.getKey(), entry.getValue());
        }
    }

    void _forceFill(Map<String, Object> attrs) {
        attributes.putAll(attrs);
    }

    private boolean isFillable(String key, List<String> fillable, List<String> guarded) {
        if (!fillable.isEmpty()) return fillable.contains(key);
        if (guarded.contains("*")) return false;
        return !guarded.contains(key);
    }

    // ─── DIRTY TRACKING ──────────────────────────────────────

    /**
     * Returns {@code true} if any attribute has been modified since the last sync.
     *
     * <p>Short-circuits on the first dirty attribute found — does not build
     * the full dirty map just to check emptiness.</p>
     */
    public boolean isDirty() {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!Objects.equals(entry.getValue(), original.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the specified attribute has been modified since the last sync.
     *
     * <p>O(1) — compares the single attribute directly instead of rebuilding the
     * entire dirty map. Safe to call on models with many attributes.</p>
     *
     * @param key the attribute name to check
     * @return {@code true} if the attribute's current value differs from the original
     */
    public boolean isDirty(String key) {
        return !Objects.equals(attributes.get(key), original.get(key));
    }

    public Map<String, Object> getDirty() {
        Map<String, Object> dirty = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!Objects.equals(entry.getValue(), original.get(entry.getKey())))
                dirty.put(entry.getKey(), entry.getValue());
        }
        return dirty;
    }

    public Map<String, Object> getOriginal() { return Collections.unmodifiableMap(original); }

    protected void syncOriginal() {
        original.clear();
        original.putAll(attributes);
    }
}