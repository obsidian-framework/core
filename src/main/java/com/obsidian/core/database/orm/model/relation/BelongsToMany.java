package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.SqlIdentifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Many-to-many relation through a pivot table.
 *
 * @param <T> related model type
 */
public class BelongsToMany<T extends Model> implements Relation<T> {

    private final Model  parent;
    private final Class<T> relatedClass;
    private final String pivotTable;
    private final String foreignPivotKey;
    private final String relatedPivotKey;
    private final List<String> pivotColumns = new ArrayList<>();

    /**
     * Creates a BelongsToMany relation.
     *
     * @param parent          parent model instance
     * @param relatedClass    related model class
     * @param pivotTable      pivot table name
     * @param foreignPivotKey pivot column referencing the parent
     * @param relatedPivotKey pivot column referencing the related model
     */
    public BelongsToMany(Model parent, Class<T> relatedClass, String pivotTable,
                         String foreignPivotKey, String relatedPivotKey) {
        this.parent           = parent;
        this.relatedClass     = relatedClass;
        this.pivotTable       = pivotTable;
        this.foreignPivotKey  = foreignPivotKey;
        this.relatedPivotKey  = relatedPivotKey;
    }

    /**
     * Includes extra pivot columns in SELECT results.
     *
     * @param columns pivot column names to retrieve
     * @return this relation
     */
    public BelongsToMany<T> withPivot(String... columns) {
        pivotColumns.addAll(Arrays.asList(columns));
        return this;
    }

    // ─── READ ────────────────────────────────────────────────

    @Override
    public List<T> get() {
        T instance      = Model.newInstance(relatedClass);
        String relTable = instance.table();
        String pk       = instance.primaryKey();

        SqlIdentifier.requireIdentifier(pivotTable);
        SqlIdentifier.requireIdentifier(foreignPivotKey);
        SqlIdentifier.requireIdentifier(relatedPivotKey);

        var qb = Model.query(relatedClass)
                .select(relTable + ".*")
                .selectRaw(pivotTable + "." + foreignPivotKey + " AS pivot_" + foreignPivotKey)
                .selectRaw(pivotTable + "." + relatedPivotKey + " AS pivot_" + relatedPivotKey);

        for (String col : pivotColumns) {
            SqlIdentifier.requireIdentifier(col);
            qb.selectRaw(pivotTable + "." + col + " AS pivot_" + col);
        }

        return qb
                .join(pivotTable, relTable + "." + pk, "=", pivotTable + "." + relatedPivotKey)
                .where(pivotTable + "." + foreignPivotKey, parent.getId())
                .get();
    }

    @Override
    public T first() {
        List<T> results = get();
        return results.isEmpty() ? null : results.get(0);
    }

    // ─── ATTACH ──────────────────────────────────────────────

    /**
     * Attaches a single related ID.
     *
     * @param relatedId related model ID
     */
    public void attach(Object relatedId) {
        attach(relatedId, Collections.emptyMap());
    }

    /**
     * Attaches a single related ID with extra pivot data.
     *
     * @param relatedId related model ID
     * @param pivotData extra columns to set on the pivot row
     */
    public void attach(Object relatedId, Map<String, Object> pivotData) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(foreignPivotKey, parent.getId());
        row.put(relatedPivotKey, relatedId);
        row.putAll(pivotData);
        new QueryBuilder(pivotTable).insert(row);
    }

    /**
     * Attaches multiple related IDs in a single JDBC batch.
     *
     * @param relatedIds IDs to attach
     */
    public void attachMany(List<Object> relatedIds) {
        if (relatedIds == null || relatedIds.isEmpty()) return;
        if (relatedIds.size() == 1) { attach(relatedIds.get(0)); return; }

        List<Map<String, Object>> rows = new ArrayList<>(relatedIds.size());
        for (Object id : relatedIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(foreignPivotKey, parent.getId());
            row.put(relatedPivotKey, id);
            rows.add(row);
        }
        new QueryBuilder(pivotTable).insertAll(rows);
    }

    // ─── DETACH ──────────────────────────────────────────────

    /**
     * Detaches a single related ID.
     *
     * @param relatedId related model ID
     */
    public void detach(Object relatedId) {
        new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .where(relatedPivotKey, relatedId)
                .delete();
    }

    /**
     * Detaches multiple related IDs in a single DELETE ... WHERE ... IN (...).
     *
     * @param relatedIds IDs to detach
     */
    public void detachMany(List<Object> relatedIds) {
        if (relatedIds == null || relatedIds.isEmpty()) return;
        if (relatedIds.size() == 1) { detach(relatedIds.get(0)); return; }

        new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .whereIn(relatedPivotKey, relatedIds)
                .delete();
    }

    /** Detaches all related IDs for this parent. */
    public void detachAll() {
        new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .delete();
    }

    // ─── SYNC ────────────────────────────────────────────────

    /**
     * Replaces all pivot entries with the given IDs.
     *
     * @param ids complete desired set of related IDs
     */
    public void sync(List<Object> ids) {
        List<Object> current   = currentRelatedIds();
        Set<String>  currentSet = toStringSet(current);
        Set<String>  targetSet  = toStringSet(ids);

        List<Object> toDetach = current.stream()
                .filter(id -> !targetSet.contains(id.toString()))
                .collect(Collectors.toList());

        List<Object> toAttach = ids.stream()
                .filter(id -> !currentSet.contains(id.toString()))
                .collect(Collectors.toList());

        if (!toDetach.isEmpty()) detachMany(toDetach);
        if (!toAttach.isEmpty()) attachMany(toAttach);
    }

    // ─── TOGGLE ──────────────────────────────────────────────

    /**
     * For each ID: attaches if absent, detaches if present.
     *
     * @param ids IDs to toggle
     */
    public void toggle(List<Object> ids) {
        if (ids == null || ids.isEmpty()) return;

        List<Object> current    = currentRelatedIds();
        Set<String>  currentSet = toStringSet(current);

        List<Object> toAttach = new ArrayList<>();
        List<Object> toDetach = new ArrayList<>();

        for (Object id : ids) {
            if (currentSet.contains(id.toString())) toDetach.add(id);
            else                                    toAttach.add(id);
        }

        if (!toDetach.isEmpty()) detachMany(toDetach);
        if (!toAttach.isEmpty()) attachMany(toAttach);
    }

    // ─── UPDATE PIVOT ────────────────────────────────────────

    /**
     * Updates pivot data for a specific related ID.
     *
     * @param relatedId related model ID
     * @param pivotData column-to-value map for the pivot row
     * @return number of affected rows
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
                .map(Model::getId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) return;

        List<Map<String, Object>> pivotRows = new QueryBuilder(pivotTable)
                .whereIn(foreignPivotKey, parentIds).get();

        List<Object> relatedIds = pivotRows.stream()
                .map(r -> r.get(relatedPivotKey)).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        if (relatedIds.isEmpty()) {
            for (Model p : parents) p.setRelation(relationName, Collections.emptyList());
            return;
        }

        T instance = Model.newInstance(relatedClass);
        List<T> allRelated = Model.query(relatedClass)
                .whereIn(instance.primaryKey(), relatedIds).get();

        Map<Object, T> relatedLookup = new LinkedHashMap<>();
        for (T m : allRelated) relatedLookup.put(m.getId(), m);

        Map<Object, List<T>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> pivot : pivotRows) {
            Object parentId  = pivot.get(foreignPivotKey);
            Object relatedId = pivot.get(relatedPivotKey);
            T related = relatedLookup.get(relatedId);
            if (related != null) grouped.computeIfAbsent(parentId, k -> new ArrayList<>()).add(related);
        }

        for (Model p : parents) {
            p.setRelation(relationName, grouped.getOrDefault(p.getId(), Collections.emptyList()));
        }
    }

    // ─── ACCESSORS ───────────────────────────────────────────

    /** @return pivot table name */
    public String getPivotTable()      { return pivotTable; }
    /** @return pivot column referencing the parent */
    public String getForeignPivotKey() { return foreignPivotKey; }
    /** @return pivot column referencing the related model */
    public String getRelatedPivotKey() { return relatedPivotKey; }

    // ─── HELPERS ─────────────────────────────────────────────

    private List<Object> currentRelatedIds() {
        return new QueryBuilder(pivotTable)
                .where(foreignPivotKey, parent.getId())
                .pluck(relatedPivotKey);
    }

    /**
     * Converts a list of IDs to a Set of strings for O(1) membership checks.
     *
     * @param ids list of IDs (may be Long or Integer)
     * @return set of string representations
     */
    private static Set<String> toStringSet(List<Object> ids) {
        Set<String> set = new HashSet<>(ids.size() * 2);
        for (Object id : ids) set.add(id.toString());
        return set;
    }
}