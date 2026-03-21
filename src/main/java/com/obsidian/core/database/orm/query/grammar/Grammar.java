package com.obsidian.core.database.orm.query.grammar;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.clause.WhereClause;

import java.util.List;
import java.util.Map;

/**
 * SQL grammar interface.
 * Each database dialect (MySQL, PostgreSQL, SQLite) implements this.
 */
public interface Grammar
{
    /**
     * Compiles a SELECT statement from the given query builder state.
     *
     * @param query query builder containing all clauses
     * @return compiled SQL string
     */
    String compileSelect(QueryBuilder query);

    /**
     * Compiles an INSERT statement for the given table and values.
     *
     * @param table  target table name
     * @param values column-to-value map
     * @return compiled {@link InsertResult}
     */
    InsertResult compileInsert(String table, Map<String, Object> values);

    /**
     * Compiles an UPDATE statement.
     *
     * @param table            target table name
     * @param values           column-to-value map for the SET clause
     * @param wheres           WHERE clauses to apply
     * @param existingBindings bindings already collected for the WHERE clause
     * @return compiled {@link UpdateResult}
     */
    UpdateResult compileUpdate(String table, Map<String, Object> values, List<WhereClause> wheres, List<Object> existingBindings);

    /**
     * Compiles a DELETE statement.
     *
     * @param table    target table name
     * @param wheres   WHERE clauses to apply
     * @param bindings bindings already collected for the WHERE clause
     * @return compiled {@link DeleteResult}
     */
    DeleteResult compileDelete(String table, List<WhereClause> wheres, List<Object> bindings);

    /**
     * Compiles an atomic increment or decrement UPDATE statement.
     *
     * @param table    target table name
     * @param column   column to increment or decrement
     * @param amount   positive to increment, negative to decrement
     * @param wheres   WHERE clauses to apply
     * @param bindings bindings already collected for the WHERE clause
     * @return compiled SQL string
     */
    String compileIncrement(String table, String column, int amount, List<WhereClause> wheres, List<Object> bindings);

    /**
     * Compiles a list of WHERE clauses into a SQL string.
     *
     * @param wheres list of WHERE clauses
     * @return compiled WHERE fragment, empty string if no clauses
     */
    String compileWheres(List<WhereClause> wheres);
}