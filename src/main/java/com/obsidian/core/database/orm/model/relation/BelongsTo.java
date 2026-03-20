package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BelongsTo relation (inverse of HasOne / HasMany).
 *
 *   // In Post model:
 *   public BelongsTo<User> author() {
 *       return belongsTo(User.class, "user_id");
 *   }
 *
 *   // Usage:
 *   User author = post.author().first();
 */
public class BelongsTo<T extends Model> implements Relation<T> {

    private final Model child;
    private final Class<T> relatedClass;
    private final String foreignKey;
    private final String ownerKey;

    /**
     * Creates a new BelongsTo instance.
     *
     * @param child The child model instance
     * @param relatedClass The class of the related model
     * @param foreignKey The foreign key column on the related table
     * @param ownerKey The primary key column on the parent model
     */
    public BelongsTo(Model child, Class<T> relatedClass, String foreignKey, String ownerKey) {
        this.child = child;
        this.relatedClass = relatedClass;
        this.foreignKey = foreignKey;
        this.ownerKey = ownerKey;
    }

    @Override
    public List<T> get() {
        T result = first();
        return result != null ? List.of(result) : Collections.emptyList();
    }

    @Override
    public T first() {
        Object fkValue = child.get(foreignKey);
        if (fkValue == null) return null;

        return Model.query(relatedClass)
                .where(ownerKey, fkValue)
                .first();
    }

    @Override
    public void eagerLoad(List<? extends Model> children, String relationName) {
        List<Object> fkValues = children.stream()
                .map(c -> c.get(foreignKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (fkValues.isEmpty()) return;

        List<T> parents = Model.query(relatedClass)
                .whereIn(ownerKey, fkValues)
                .get();

        // Build lookup: ownerKey value -> parent model
        Map<Object, T> lookup = new LinkedHashMap<>();
        for (T p : parents) {
            lookup.put(p.get(ownerKey), p);
        }

        // Assign to each child
        for (Model child : children) {
            Object key = child.get(foreignKey);
            T match = lookup.get(key);
            child.setRelation(relationName, match != null ? List.of(match) : Collections.emptyList());
        }
    }

    /**
     * Associate this child with a parent.
     */
    public void associate(T parent) {
        child.set(foreignKey, parent.get(ownerKey));
    }

    /**
     * Dissociate (set foreign key to null).
     */
    public void dissociate() {
        child.set(foreignKey, null);
    }

    /**
     * Returns the foreign key.
     *
     * @return The foreign key
     */
    public String getForeignKey() { return foreignKey; }
    /**
     * Returns the owner key.
     *
     * @return The owner key
     */
    public String getOwnerKey()   { return ownerKey; }
}
