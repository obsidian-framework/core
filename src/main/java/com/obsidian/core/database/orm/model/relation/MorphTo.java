package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;

import java.util.*;

/**
 * Polymorphic inverse relation (MorphTo).
 *
 * Example: Comment belongs to either Post or Video.
 *
 *   // In Comment model:
 *   public MorphTo<?> commentable() {
 *       return morphTo("commentable", Map.of(
 *           "Post", Post.class,
 *           "Video", Video.class
 *       ));
 *   }
 *
 *   Model parent = comment.commentable().first();
 */
public class MorphTo<T extends Model> implements Relation<T> {

    private final Model child;
    private final String morphIdKey;
    private final String morphTypeKey;
    private final Map<String, Class<? extends Model>> morphMap;

    /**
     * Creates a new MorphTo instance.
     *
     * @param child The child model instance
     * @param morphName The morph name prefix (e.g. "commentable" for commentable_id/commentable_type)
     * @param morphMap Map of type strings to model classes for polymorphic resolution
     */
    public MorphTo(Model child, String morphName, Map<String, Class<? extends Model>> morphMap) {
        this.child = child;
        this.morphIdKey = morphName + "_id";
        this.morphTypeKey = morphName + "_type";
        this.morphMap = morphMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Returns the .
     *
     * @return The 
     */
    public List<T> get() {
        T result = first();
        return result != null ? List.of(result) : Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Executes the query and returns the first result, or null.
     *
     * @return The model instance, or {@code null} if not found
     */
    public T first() {
        String type = child.getString(morphTypeKey);
        Object id = child.get(morphIdKey);

        if (type == null || id == null) return null;

        Class<? extends Model> modelClass = morphMap.get(type);
        if (modelClass == null) {
            throw new RuntimeException("Unknown morph type: " + type
                    + ". Register it in the morphMap.");
        }

        return (T) Model.find(modelClass, id);
    }

    @Override
    public void eagerLoad(List<? extends Model> children, String relationName) {
        // Group children by morph type
        Map<String, List<Model>> byType = new LinkedHashMap<>();
        for (Model child : children) {
            String type = child.getString(morphTypeKey);
            if (type != null) {
                byType.computeIfAbsent(type, k -> new ArrayList<>()).add(child);
            }
        }

        // For each type, load all parents in one query
        for (Map.Entry<String, List<Model>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<Model> typedChildren = entry.getValue();

            Class<? extends Model> modelClass = morphMap.get(type);
            if (modelClass == null) continue;

            List<Object> ids = new ArrayList<>();
            for (Model c : typedChildren) {
                Object id = c.get(morphIdKey);
                if (id != null) ids.add(id);
            }

            if (ids.isEmpty()) continue;

            Model instance = Model.newInstance(modelClass);
            List<? extends Model> parents = Model.query(modelClass)
                    .whereIn(instance.primaryKey(), ids)
                    .get();

            // Build lookup
            Map<Object, Model> lookup = new LinkedHashMap<>();
            for (Model p : parents) {
                lookup.put(p.getId(), p);
            }

            // Assign
            for (Model c : typedChildren) {
                Object id = c.get(morphIdKey);
                Model match = lookup.get(id);
                c.setRelation(relationName, match != null ? List.of(match) : Collections.emptyList());
            }
        }

        // Set empty relation for children without a type
        for (Model child : children) {
            if (!child.relationLoaded(relationName)) {
                child.setRelation(relationName, Collections.emptyList());
            }
        }
    }
}
