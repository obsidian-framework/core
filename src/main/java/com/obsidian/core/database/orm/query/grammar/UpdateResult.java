package com.obsidian.core.database.orm.query.grammar;

import java.util.List;

public class UpdateResult
{
    private final String sql;
    private final List<Object> bindings;

    /**
     * Creates a new update result.
     *
     * @param sql      compiled SQL string
     * @param bindings parameter values bound to the query
     */
    public UpdateResult(String sql, List<Object> bindings)
    {
        this.sql = sql;
        this.bindings = bindings;
    }

    /**
     * Returns the compiled SQL string.
     *
     * @return SQL string
     */
    public String getSql() { return sql; }

    /**
     * Returns the parameter bindings.
     *
     * @return list of bound values
     */
    public List<Object> getBindings() { return bindings; }
}