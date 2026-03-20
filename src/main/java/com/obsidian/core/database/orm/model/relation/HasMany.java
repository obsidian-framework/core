package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HasMany relation.
 *
 *   // In User model:
 *   public HasMany<Post> posts() {
 *       return hasMany(Post.class, "user_id");
 *   }
 *
 *   // Usage:
 *   List<Post> posts = user.posts().get();
 */
public class HasMany<T extends Model> implements Relation<T> {

    private final Model parent;
    private final Class<T> relatedClass;
    private final String foreignKey;
    private final String localKey;

    /**
     * Creates a new HasMany instance.
     *
     * @param parent The parent model instance
     * @param relatedClass The class of the related model
     * @param foreignKey The foreign key column on the related table
     * @param localKey The local key column on this model's table
     */
    public HasMany(Model parent, Class<T> relatedClass, String foreignKey, String localKey) {
        this.parent = parent;
        this.relatedClass = relatedClass;
        this.foreignKey = foreignKey;
        this.localKey = localKey;
    }

    @Override
    public List<T> get() {
        Object parentKeyValue = parent.get(localKey);
        if (parentKeyValue == null) return Collections.emptyList();

        return Model.query(relatedClass)
                .where(foreignKey, parentKeyValue)
                .get();
    }

    @Override
    public T first() {
        Object parentKeyValue = parent.get(localKey);
        if (parentKeyValue == null) return null;

        return Model.query(relatedClass)
                .where(foreignKey, parentKeyValue)
                .first();
    }

    @Override
    public void eagerLoad(List<? extends Model> parents, String relationName) {
        List<Object> parentIds = parents.stream()
                .map(p -> p.get(localKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        List<T> related = Model.query(relatedClass)
                .whereIn(foreignKey, parentIds)
                .get();

        // Group by foreign key value
        Map<Object, List<T>> grouped = new LinkedHashMap<>();
        for (T model : related) {
            Object fkValue = model.get(foreignKey);
            grouped.computeIfAbsent(fkValue, k -> new ArrayList<>()).add(model);
        }

        for (Model parent : parents) {
            Object key = parent.get(localKey);
            parent.setRelation(relationName, grouped.getOrDefault(key, Collections.emptyList()));
        }
    }

    /**
     * Create a new related model and set the foreign key.
     */
    public T create(Map<String, Object> attributes) {
        T model = Model.newInstance(relatedClass);
        model.fill(attributes);
        model.set(foreignKey, parent.get(localKey));
        model.save();
        return model;
    }

    /**
     * Returns the foreign key.
     *
     * @return The foreign key
     */
    public String getForeignKey() { return foreignKey; }
    /**
     * Returns the local key.
     *
     * @return The local key
     */
    public String getLocalKey()   { return localKey; }
}
