package com.obsidian.core.database.orm.model;

import com.obsidian.core.database.orm.model.cast.AttributeCaster;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Attribute access, type coercion, mass-assignment, and dirty tracking.
 * Package-private and abstract — only {@link Model} is public.
 * Fluent methods return void here; Model redeclares them returning Model.
 */
abstract class ModelAttributes
{
    final Map<String, Object> attributes = new LinkedHashMap<>();
    final Map<String, Object> original   = new LinkedHashMap<>();

    abstract ModelMetadata meta();

    /**
     * Returns the cast value of the given attribute, applying any registered cast.
     *
     * @param key attribute name
     * @return the cast value, or the raw value if no cast is registered
     */
    public Object get(String key)
    {
        Object value = attributes.get(key);
        String castType = meta().casts.get(key);
        if (castType != null && value != null) return AttributeCaster.castGet(value, castType);
        return value;
    }

    /**
     * Returns the raw value of the given attribute, bypassing cast handling.
     *
     * @param key attribute name
     * @return the raw stored value
     */
    public Object getRaw(String key) { return attributes.get(key); }

    /**
     * Returns the value of the given attribute as a {@link String}.
     *
     * @param key attribute name
     * @return string representation, or {@code null} if the attribute is absent
     */
    public String getString(String key) {
        Object val = get(key); return val != null ? val.toString() : null;
    }

    /**
     * Returns the value of the given attribute as an {@link Integer}.
     *
     * @param key attribute name
     * @return integer value, or {@code null} if the attribute is absent
     */
    public Integer getInteger(String key)
    {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    /**
     * Returns the value of the given attribute as a {@code long}.
     *
     * @param key attribute name
     * @return long value, or {@code 0} if the attribute is absent
     */
    public long getLong(String key)
    {
        Object value = attributes.get(key);
        if (value == null) return 0L;
        if (value instanceof Long l)      return l;
        if (value instanceof Integer i)   return i.longValue();
        if (value instanceof Timestamp ts) return ts.getTime();
        if (value instanceof java.sql.Date d) return d.getTime();
        if (value instanceof java.util.Date d) return d.getTime();
        return Long.parseLong(value.toString());
    }

    /**
     * Returns the value of the given attribute as a {@link Double}.
     *
     * @param key attribute name
     * @return double value, or {@code null} if the attribute is absent
     */
    public Double getDouble(String key)
    {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(val.toString());
    }

    /**
     * Returns the value of the given attribute as a {@link Boolean}.
     *
     * @param key attribute name
     * @return boolean value, or {@code null} if the attribute is absent
     */
    public Boolean getBoolean(String key)
    {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        return Boolean.parseBoolean(val.toString());
    }

    /**
     * Returns the value of the given attribute as a {@link LocalDateTime}.
     *
     * @param key attribute name
     * @return date-time value, or {@code null} if the attribute is absent
     */
    public LocalDateTime getDateTime(String key)
    {
        Object val = get(key);
        if (val == null) return null;
        if (val instanceof LocalDateTime) return (LocalDateTime) val;
        if (val instanceof java.sql.Timestamp) return ((java.sql.Timestamp) val).toLocalDateTime();
        return LocalDateTime.parse(val.toString());
    }

    // Return void — Model redeclares these returning Model for fluent chaining.
    // Java allows a subclass to redeclare (hide) a void method with a covariant
    // return type when it's not an @Override of the same signature.

    /**
     * Sets an attribute value, applying any registered cast.
     *
     * @param key   attribute name
     * @param value value to assign
     */
    void _set(String key, Object value) {
        String castType = meta().casts.get(key);
        if (castType != null && value != null) value = AttributeCaster.castSet(value, castType);
        attributes.put(key, value);
    }

    /**
     * Sets an attribute value, bypassing cast handling.
     *
     * @param key   attribute name
     * @param value raw value to assign
     */
    void _setRaw(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Returns the primary key value of this model.
     *
     * @return the primary key value, or {@code null} if not set
     */
    public Object getId() { return attributes.get(meta().primaryKey); }

    /**
     * Returns an unmodifiable view of all current attributes.
     *
     * @return unmodifiable attribute map
     */
    public Map<String, Object> getAttributes() { return Collections.unmodifiableMap(attributes); }

    /**
     * Mass-assigns attributes, respecting fillable and guarded rules.
     *
     * @param attrs attribute map to assign
     */
    void _fill(Map<String, Object> attrs) {
        List<String> fillable = meta().fillable;
        List<String> guarded  = meta().guarded;
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            if (isFillable(entry.getKey(), fillable, guarded))
                _set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Mass-assigns attributes, bypassing fillable and guarded rules.
     *
     * @param attrs attribute map to assign
     */
    void _forceFill(Map<String, Object> attrs) {
        attributes.putAll(attrs);
    }

    private boolean isFillable(String key, List<String> fillable, List<String> guarded) {
        if (!fillable.isEmpty()) return fillable.contains(key);
        if (guarded.contains("*")) return false;
        return !guarded.contains(key);
    }

    /**
     * Returns {@code true} if any attribute has been modified since the last sync.
     *
     * @return {@code true} if the model has unsaved changes
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
     * Returns {@code true} if the given attribute has been modified since the last sync.
     *
     * @param key attribute name to check
     * @return {@code true} if the attribute's current value differs from the original
     */
    public boolean isDirty(String key) {
        return !Objects.equals(attributes.get(key), original.get(key));
    }

    /**
     * Returns all attributes that have been modified since the last sync.
     *
     * @return map of dirty attribute names to their current values
     */
    public Map<String, Object> getDirty() {
        Map<String, Object> dirty = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (!Objects.equals(entry.getValue(), original.get(entry.getKey())))
                dirty.put(entry.getKey(), entry.getValue());
        }
        return dirty;
    }

    /**
     * Returns an unmodifiable view of the original attribute values at the last sync.
     *
     * @return unmodifiable map of original attributes
     */
    public Map<String, Object> getOriginal() { return Collections.unmodifiableMap(original); }

    /**
     * Syncs the original attribute snapshot to the current attribute values.
     */
    protected void syncOriginal() {
        original.clear();
        original.putAll(attributes);
    }
}