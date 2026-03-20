package com.obsidian.core.database.orm.model;

import java.util.*;

/**
 * Serialization (toMap) and hydration (hydrate, hydrateList).
 *
 * <p>Extracted from {@link Model} to keep each concern in a single file.</p>
 */
abstract class ModelSerializer extends ModelRelations {

    // ─── SERIALIZATION ───────────────────────────────────────

    /**
     * Converts this model to a plain map, excluding {@link Model#hidden()} fields
     * and including any eagerly loaded relations (recursively serialized).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(attributes);

        for (String key : meta().hidden) {
            map.remove(key);
        }

        for (Map.Entry<String, List<? extends Model>> entry : getLoadedRelations().entrySet()) {
            List<Map<String, Object>> relMaps = new ArrayList<>();
            for (Model m : entry.getValue()) {
                relMaps.add(m.toMap());
            }
            map.put(entry.getKey(), relMaps);
        }

        return map;
    }

    // ─── HYDRATION ───────────────────────────────────────────

    /**
     * Creates a model instance from a raw database row and marks it as persisted.
     */
    public static <T extends Model> T hydrate(Class<T> modelClass, Map<String, Object> row) {
        T model = Model.newInstance(modelClass);
        model.hydrateAttributes(row);
        return model;
    }

    /**
     * Creates model instances from a list of raw database rows.
     */
    public static <T extends Model> List<T> hydrateList(Class<T> modelClass, List<Map<String, Object>> rows) {
        List<T> models = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            models.add(hydrate(modelClass, row));
        }
        return models;
    }

    /**
     * Internal hydration — fills attributes and marks the model as persisted.
     * Package-private so {@link ModelQueryBuilder} can call it without reflection.
     */
    void hydrateAttributes(Map<String, Object> row) {
        attributes.putAll(row);
        exists = true;
        syncOriginal();
    }
}
