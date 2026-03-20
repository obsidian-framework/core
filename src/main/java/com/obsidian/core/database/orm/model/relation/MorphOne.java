package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Polymorphic one-to-one relation.
 *
 * Example: Both User and Team can have one Image.
 *
 *   // images table: id, url, imageable_id, imageable_type
 *
 *   // In User model:
 *   public MorphOne<Image> image() {
 *       return morphOne(Image.class, "imageable");
 *   }
 */
public class MorphOne<T extends Model> implements Relation<T> {

    private final Model parent;
    private final Class<T> relatedClass;
    private final String morphIdKey;
    private final String morphTypeKey;
    private final String localKey;

    /**
     * Creates a new MorphOne instance.
     *
     * @param parent The parent model instance
     * @param relatedClass The class of the related model
     * @param morphName The morph name prefix (e.g. "commentable" for commentable_id/commentable_type)
     * @param localKey The local key column on this model's table
     */
    public MorphOne(Model parent, Class<T> relatedClass, String morphName, String localKey) {
        this.parent = parent;
        this.relatedClass = relatedClass;
        this.morphIdKey = morphName + "_id";
        this.morphTypeKey = morphName + "_type";
        this.localKey = localKey;
    }

    private String getMorphType() {
        return parent.getClass().getSimpleName();
    }

    @Override
    public List<T> get() {
        T result = first();
        return result != null ? List.of(result) : Collections.emptyList();
    }

    @Override
    public T first() {
        Object parentKeyValue = parent.get(localKey);
        if (parentKeyValue == null) return null;

        return Model.query(relatedClass)
                .where(morphTypeKey, getMorphType())
                .where(morphIdKey, parentKeyValue)
                .first();
    }

    @Override
    public void eagerLoad(List<? extends Model> parents, String relationName) {
        if (parents.isEmpty()) return;

        String morphType = parents.get(0).getClass().getSimpleName();

        List<Object> parentIds = parents.stream()
                .map(p -> p.get(localKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        List<T> related = Model.query(relatedClass)
                .where(morphTypeKey, morphType)
                .whereIn(morphIdKey, parentIds)
                .get();

        Map<Object, T> lookup = new LinkedHashMap<>();
        for (T model : related) {
            lookup.put(model.get(morphIdKey), model);
        }

        for (Model p : parents) {
            Object key = p.get(localKey);
            T match = lookup.get(key);
            p.setRelation(relationName, match != null ? List.of(match) : Collections.emptyList());
        }
    }
}
