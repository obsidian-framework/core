package com.obsidian.core.database.orm.model;

import java.util.*;

/**
 * Serialization (toMap) and hydration (hydrate, hydrateList).
 * Extracted from {@link Model} to keep each concern in a single file.
 */
abstract class ModelSerializer extends ModelRelations
{
    /**
     * Converts this model to a plain map, excluding {@link Model#hidden()} fields
     * and including any eagerly loaded relations serialized recursively.
     *
     * @return attribute map with hidden fields removed and relations included
     */
    public Map<String, Object> toMap()
    {
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

    /**
     * Creates a model instance from a raw database row and marks it as persisted.
     *
     * @param modelClass model class to instantiate
     * @param row        raw row data from the database
     * @return hydrated model instance
     */
    public static <T extends Model> T hydrate(Class<T> modelClass, Map<String, Object> row)
    {
        T model = Model.newInstance(modelClass);
        model.hydrateAttributes(row);
        return model;
    }

    /**
     * Creates model instances from a list of raw database rows.
     *
     * @param modelClass model class to instantiate
     * @param rows       list of raw row data from the database
     * @return list of hydrated model instances
     */
    public static <T extends Model> List<T> hydrateList(Class<T> modelClass, List<Map<String, Object>> rows)
    {
        List<T> models = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            models.add(hydrate(modelClass, row));
        }
        return models;
    }

    /**
     * Fills attributes from a raw row and marks the model as persisted.
     * Package-private so {@link ModelQueryBuilder} can call it without reflection.
     *
     * @param row raw row data from the database
     */
    void hydrateAttributes(Map<String, Object> row)
    {
        attributes.putAll(row);
        exists = true;
        syncOriginal();
    }
}