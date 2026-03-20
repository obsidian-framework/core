package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.SqlIdentifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BelongsToMany relation (many-to-many through pivot table).
 *
 *   // In User model:
 *   public BelongsToMany<Role> roles() {
 *       return belongsToMany(Role.class, "role_user", "user_id", "role_id");
 *   }
 *
 *   // Usage:
 *   List<Role> roles = user.roles().get();
 *   user.roles().attach(roleId);
 *   user.roles().detach(roleId);
 *   user.roles().sync(List.of(1, 2, 3));
 */
public class BelongsToMany<T extends Model> implements Relation<T> {

    private final Model parent;
    private final Class<T> relatedClass;
    private final String pivotTable;
    private final String foreignPivotKey;
    private final String relatedPivotKey;

    // Optional pivot columns to retrieve
    private final List<String> pivotColumns = new ArrayList<>();

    /**
     * Creates a new BelongsToMany relation.
     *
     * @param parent          The parent model instance
     * @param relatedClass    The related model class
     * @param pivotTable      The pivot (join) table name
     * @param foreignPivotKey The column on the pivot table referencing the parent model
     * @param relatedPivotKey The column on the pivot table referencing the related model
     */
    public BelongsToMany(Model parent, Class<T> relatedClass, String pivotTable,
                         String foreignPivotKey, String relatedPivotKey) {
        this.parent = parent;
        this.relatedClass = relatedClass;
        this.pivotTable = pivotTable;
        this.foreignPivotKey = foreignPivotKey;
        this.relatedPivotKey = relatedPivotKey;
    }

    /**
     * Specify additional pivot columns to retrieve.
     */
    public BelongsToMany<T> withPivot(String... columns) {
        pivotColumns.addAll(Arrays.asList(columns));
        return this;
    }

    @Override
    public List<T> get() {
        T instance = Model.newInstance(relatedClass);
        String relatedTable = instance.table();
        String pk = instance.primaryKey();

        // Validate every identifier component individually before interpolating.
        // The resulting "table.col AS alias" expressions are raw SQL and must go
        // through selectRaw — they are not plain identifiers and would be (correctly)
        // rejected by requireIdentifier after the bypass removal.
        SqlIdentifier.requireIdentifier(pivotTable);
        SqlIdentifier.requireIdentifier(foreignPivotKey);
        SqlIdentifier.requireIdentifier(relatedPivotKey);

        var qb = Model.query(relatedClass)
                .select(relatedTable + ".*")
                .selectRaw(pivotTable + "." + foreignPivotKey + " AS pivot_" + foreignPivotKey)
                .selectRaw(pivotTable + "." + relatedPivotKey + " AS pivot_" + relatedPivotKey);

        for (String col : pivotColumns) {
            SqlIdentifier.requireIdentifier(col);
            qb.selectRaw(pivotTable + "." + col + " AS pivot_" + col);
        }

        return qb
                .join(pivotTable, relatedTable + "." + pk, "=", pivotTable + "." + relatedPivotKey)
                .where(pivotTable + "." + foreignPivotKey, parent.getId())
                .get();
    }

    @Override
    public T first() {
        List<T> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    // ─── PIVOT OPERATIONS ────────────────────────────────────

    /**
     * Attach a related model by ID.
     */
    public void attach(Object relatedId) {
        attach(relatedId, Collections.emptyMap());
    }

    /**
     * Attach with extra pivot data.
     */
    public void attach(Object relatedId, Map<String, Object> pivotData) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(foreignPivotKey, parent.getId());
        row.put(relatedPivotKey, relatedId);
        row.putAll(pivotData);

        new QueryBuilder(pivotTable).insert(row);
    }

    /**
     * Attach multiple IDs.
     */
    public void attachMany(List<Object> relatedIds) {
        for (Object id : relatedIds) {
            attach(id);
        }
    }

    /**
     * Detach a related model by ID.
     */
    public void detach(Object relatedId) {
        new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .where(relatedPivotKey, relatedId)
                .delete();
    }

    /**
     * Detach multiple IDs.
     */
    public void detachMany(List<Object> relatedIds) {
        for (Object id : relatedIds) {
            detach(id);
        }
    }

    /**
     * Detach all related models.
     */
    public void detachAll() {
        new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .delete();
    }

    /**
     * Sync: replace all pivot entries with given IDs.
     * Detaches IDs not in the list, attaches new ones.
     */
    public void sync(List<Object> ids) {
        // Get current pivot entries
        List<Object> currentIds = new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .pluck(relatedPivotKey);

        // Detach removed
        for (Object current : currentIds) {
            if (!ids.contains(current)) {
                detach(current);
            }
        }

        // Attach new
        for (Object id : ids) {
            if (!currentIds.contains(id)) {
                attach(id);
            }
        }
    }

    /**
     * Toggle: attach if not present, detach if present.
     */
    public void toggle(List<Object> ids) {
        List<Object> currentIds = new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .pluck(relatedPivotKey);

        for (Object id : ids) {
            if (currentIds.contains(id)) {
                detach(id);
            } else {
                attach(id);
            }
        }
    }

    /**
     * Update pivot data for a specific related ID.
     */
    public int updatePivot(Object relatedId, Map<String, Object> pivotData) {
        return new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .where(relatedPivotKey, relatedId)
                .update(pivotData);
    }

    // ─── EAGER LOADING ───────────────────────────────────────

    @Override
    public void eagerLoad(List<? extends Model> parents, String relationName) {
        List<Object> parentIds = parents.stream()
                .map(Model::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        // 1. Load all pivot rows for these parents
        List<Map<String, Object>> pivotRows = new QueryBuilder(pivotTable)
                .whereIn(foreignPivotKey, parentIds)
                .get();

        // 2. Collect all related IDs
        List<Object> relatedIds = pivotRows.stream()
                .map(r -> r.get(relatedPivotKey))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (relatedIds.isEmpty()) {
            for (Model p : parents) {
                p.setRelation(relationName, Collections.emptyList());
            }
            return;
        }

        // 3. Load all related models in one query
        T instance = Model.newInstance(relatedClass);
        List<T> allRelated = Model.query(relatedClass)
                .whereIn(instance.primaryKey(), relatedIds)
                .get();

        // 4. Build lookup: related ID -> model
        Map<Object, T> relatedLookup = new LinkedHashMap<>();
        for (T m : allRelated) {
            relatedLookup.put(m.getId(), m);
        }

        // 5. Group by parent ID
        Map<Object, List<T>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> pivot : pivotRows) {
            Object parentId = pivot.get(foreignPivotKey);
            Object relatedId = pivot.get(relatedPivotKey);
            T related = relatedLookup.get(relatedId);
            if (related != null) {
                grouped.computeIfAbsent(parentId, k -> new ArrayList<>()).add(related);
            }
        }

        // 6. Assign to each parent
        for (Model p : parents) {
            p.setRelation(relationName, grouped.getOrDefault(p.getId(), Collections.emptyList()));
        }
    }

    /**
     * Returns the pivot table.
     *
     * @return The pivot table
     */
    public String getPivotTable()      { return pivotTable; }
    /**
     * Returns the foreign pivot key.
     *
     * @return The foreign pivot key
     */
    public String getForeignPivotKey() { return foreignPivotKey; }
    /**
     * Returns the related pivot key.
     *
     * @return The related pivot key
     */
    public String getRelatedPivotKey() { return relatedPivotKey; }
}