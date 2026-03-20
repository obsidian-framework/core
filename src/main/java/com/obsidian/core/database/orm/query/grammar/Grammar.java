package com.obsidian.core.database.orm.query.grammar;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.clause.WhereClause;

import java.util.List;
import java.util.Map;

/**
 * SQL grammar interface.
 * Each database dialect (MySQL, PostgreSQL, SQLite) implements this.
 */
public interface Grammar {

    String compileSelect(QueryBuilder query);

    InsertResult compileInsert(String table, Map<String, Object> values);

    UpdateResult compileUpdate(String table, Map<String, Object> values,
                               List<WhereClause> wheres, List<Object> bindings);

    DeleteResult compileDelete(String table, List<WhereClause> wheres, List<Object> bindings);

    String compileIncrement(String table, String column, int amount,
                            List<WhereClause> wheres, List<Object> bindings);

    String compileWheres(List<WhereClause> wheres);
}
