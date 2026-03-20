package com.obsidian.core.database.orm.query.grammar;

import java.util.List;

public class DeleteResult {

    private final String sql;
    private final List<Object> bindings;

    /**
     * Creates a new DeleteResult instance.
     *
     * @param sql Raw SQL string
     * @param bindings Parameter values bound to the query
     */
    public DeleteResult(String sql, List<Object> bindings) {
        this.sql = sql;
        this.bindings = bindings;
    }

    /**
     * Returns the sql.
     *
     * @return The sql
     */
    public String getSql()           { return sql; }
    /**
     * Returns the bindings.
     *
     * @return The bindings
     */
    public List<Object> getBindings() { return bindings; }
}
