package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Polymorphic one-to-many relation.
 *
 * Example: Both Post and Video can have Comments.
 *
 *   // comments table: id, body, commentable_id, commentable_type
 *
 *   // In Post model:
 *   public MorphMany<Comment> comments() {
 *       return morphMany(Comment.class, "commentable");
 *   }
 *
 *   // In Video model:
 *   public MorphMany<Comment> comments() {
 *       return morphMany(Comment.class, "commentable");
 *   }
 *
 *   List<Comment> comments = post.comments().get();
 */
public class MorphMany<T extends Model> implements Relation<T> {

    private final Model parent;
    private final Class<T> relatedClass;
    private final String morphName;     // e.g. "commentable"
    private final String morphIdKey;    // e.g. "commentable_id"
    private final String morphTypeKey;  // e.g. "commentable_type"
    private final String localKey;

    /**
     * Creates a new MorphMany instance.
     *
     * @param parent The parent model instance
     * @param relatedClass The class of the related model
     * @param morphName The morph name prefix (e.g. "commentable" for commentable_id/commentable_type)
     * @param localKey The local key column on this model's table
     */
    public MorphMany(Model parent, Class<T> relatedClass, String morphName, String localKey) {
        this.parent = parent;
        this.relatedClass = relatedClass;
        this.morphName = morphName;
        this.morphIdKey = morphName + "_id";
        this.morphTypeKey = morphName + "_type";
        this.localKey = localKey;
    }

    private String getMorphType() {
        return parent.getClass().getSimpleName();
    }

    @Override
    public List<T> get() {
        Object parentKeyValue = parent.get(localKey);
        if (parentKeyValue == null) return Collections.emptyList();

        return Model.query(relatedClass)
                .where(morphTypeKey, getMorphType())
                .where(morphIdKey, parentKeyValue)
                .get();
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

    /**
     * Create a new related model with morph columns set.
     */
    public T create(Map<String, Object> attributes) {
        T model = Model.newInstance(relatedClass);
        model.fill(attributes);
        model.set(morphIdKey, parent.get(localKey));
        model.set(morphTypeKey, getMorphType());
        model.save();
        return model;
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

        // Group by morph ID
        Map<Object, List<T>> grouped = new LinkedHashMap<>();
        for (T model : related) {
            Object key = model.get(morphIdKey);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(model);
        }

        for (Model p : parents) {
            Object key = p.get(localKey);
            p.setRelation(relationName, grouped.getOrDefault(key, Collections.emptyList()));
        }
    }
}
