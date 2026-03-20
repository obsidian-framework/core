package com.obsidian.core.database.orm.model.relation;

import com.obsidian.core.database.orm.model.Model;

import java.util.List;

/**
 * Base relation interface.
 */
public interface Relation<T extends Model> {

    /**
     * Get the results of the relation (lazy load).
     */
    List<T> get();

    /**
     * Get first result.
     */
    T first();

    /**
     * Eager load this relation for a collection of parent models.
     */
    void eagerLoad(List<? extends Model> parents, String relationName);
}
