package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.query.QueryBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * HasManyThrough relation.
 *
 * Example: Country has many Posts through Users.
 *
 *   // In Country model:
 *   public HasManyThrough<Post> posts() {
 *       return hasManyThrough(
 *           Post.class,        // final model
 *           User.class,        // intermediate model
 *           "country_id",      // FK on intermediate (users.country_id)
 *           "user_id",         // FK on final (posts.user_id)
 *           "id",              // local key on Country
 *           "id"               // local key on User
 *       );
 *   }
 *
 *   List<Post> posts = country.posts().get();
 */
public class HasManyThrough<T extends Model> implements Relation<T> {

    private final Model parent;
    private final Class<T> relatedClass;
    private final Class<? extends Model> throughClass;
    private final String firstKey;      // FK on intermediate table
    private final String secondKey;     // FK on final table
    private final String localKey;      // PK on parent
    private final String secondLocalKey; // PK on intermediate

    /**
     * Creates a new HasManyThrough relation.
     *
     * @param parent         The parent model instance
     * @param relatedClass   The final related model class
     * @param throughClass   The intermediate model class
     * @param firstKey       Foreign key on the intermediate table
     * @param secondKey      Foreign key on the final table
     * @param localKey       Local key on the parent model
     * @param secondLocalKey Local key on the intermediate model
     */
    public HasManyThrough(Model parent, Class<T> relatedClass, Class<? extends Model> throughClass,
                           String firstKey, String secondKey, String localKey, String secondLocalKey) {
        this.parent = parent;
        this.relatedClass = relatedClass;
        this.throughClass = throughClass;
        this.firstKey = firstKey;
        this.secondKey = secondKey;
        this.localKey = localKey;
        this.secondLocalKey = secondLocalKey;
    }

    @Override
    public List<T> get() {
        Object parentKeyValue = parent.get(localKey);
        if (parentKeyValue == null) return Collections.emptyList();

        Model throughInstance = Model.newInstance(throughClass);
        T relatedInstance = Model.newInstance(relatedClass);

        String throughTable = throughInstance.table();
        String relatedTable = relatedInstance.table();

        // SELECT related.* FROM related
        // JOIN through ON through.secondLocalKey = related.secondKey
        // WHERE through.firstKey = ?
        return Model.query(relatedClass)
                .join(throughTable,
                        throughTable + "." + secondLocalKey, "=",
                        relatedTable + "." + secondKey)
                .where(throughTable + "." + firstKey, parentKeyValue)
                .get();
    }

    @Override
    public T first() {
        List<T> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void eagerLoad(List<? extends Model> parents, String relationName) {
        List<Object> parentIds = parents.stream()
                .map(p -> p.get(localKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        Model throughInstance = Model.newInstance(throughClass);
        T relatedInstance = Model.newInstance(relatedClass);

        String throughTable = throughInstance.table();
        String relatedTable = relatedInstance.table();

        // Load all through records for these parents
        List<Map<String, Object>> throughRows = new QueryBuilder(throughTable)
                .select(secondLocalKey, firstKey)
                .whereIn(firstKey, parentIds)
                .get();

        if (throughRows.isEmpty()) {
            for (Model p : parents) {
                p.setRelation(relationName, Collections.emptyList());
            }
            return;
        }

        // Collect intermediate IDs
        List<Object> throughIds = throughRows.stream()
                .map(r -> r.get(secondLocalKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Load all final models
        List<T> allRelated = Model.query(relatedClass)
                .whereIn(secondKey, throughIds)
                .get();

        // Build lookup: secondKey value -> list of related models
        Map<Object, List<T>> relatedByThrough = new LinkedHashMap<>();
        for (T model : allRelated) {
            Object key = model.get(secondKey);
            relatedByThrough.computeIfAbsent(key, k -> new ArrayList<>()).add(model);
        }

        // Build mapping: parentId -> through secondLocalKey values
        Map<Object, List<Object>> parentToThrough = new LinkedHashMap<>();
        for (Map<String, Object> row : throughRows) {
            Object parentId = row.get(firstKey);
            Object throughId = row.get(secondLocalKey);
            parentToThrough.computeIfAbsent(parentId, k -> new ArrayList<>()).add(throughId);
        }

        // Assign to parents
        for (Model p : parents) {
            Object pId = p.get(localKey);
            List<Object> throughIdsForParent = parentToThrough.getOrDefault(pId, Collections.emptyList());
            List<T> related = new ArrayList<>();
            for (Object tId : throughIdsForParent) {
                List<T> matches = relatedByThrough.getOrDefault(tId, Collections.emptyList());
                related.addAll(matches);
            }
            p.setRelation(relationName, related);
        }
    }
}
