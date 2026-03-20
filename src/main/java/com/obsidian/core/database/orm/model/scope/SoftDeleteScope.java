package com.obsidian.core.database.orm.model.scope;

import com.obsidian.core.database.orm.query.QueryBuilder;

/**
 * Global scope that excludes soft-deleted records.
 * Automatically applied when a model has softDeletes() = true.
 */
public class SoftDeleteScope implements Scope {

    @Override
    public void apply(QueryBuilder query) {
        query.whereNull("deleted_at");
    }
}
