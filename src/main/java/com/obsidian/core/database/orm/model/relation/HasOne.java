package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.model.ModelQueryBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HasOne relation.
 *
 *   // In User model:
 *   public HasOne<Profile> profile() {
 *       return hasOne(Profile.class, "user_id");
 *   }
 *
 *   // Usage:
 *   Profile p = user.profile().first();
 */
public class HasOne<T extends Model> implements Relation<T> {

    private final Model parent;
    private final Class<T> relatedClass;
    private final String foreignKey;
    private final String localKey;

    /**
     * Creates a new HasOne instance.
     *
     * @param parent The parent model instance
     * @param relatedClass The class of the related model
     * @param foreignKey The foreign key column on the related table
     * @param localKey The local key column on this model's table
     */
    public HasOne(Model parent, Class<T> relatedClass, String foreignKey, String localKey) {
        this.parent = parent;
        this.relatedClass = relatedClass;
        this.foreignKey = foreignKey;
        this.localKey = localKey;
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
                .where(foreignKey, parentKeyValue)
                .first();
    }

    @Override
    public void eagerLoad(List<? extends Model> parents, String relationName) {
        // Collect all parent key values
        List<Object> parentIds = parents.stream()
                .map(p -> p.get(localKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        // Single query for all related models
        List<T> related = Model.query(relatedClass)
                .whereIn(foreignKey, parentIds)
                .get();

        // Build lookup map: foreignKey value -> related model
        Map<Object, T> lookup = new LinkedHashMap<>();
        for (T model : related) {
            lookup.put(model.get(foreignKey), model);
        }

        // Assign to parents
        for (Model parent : parents) {
            Object key = parent.get(localKey);
            T match = lookup.get(key);
            parent.setRelation(relationName, match != null ? List.of(match) : Collections.emptyList());
        }
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
