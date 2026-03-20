package com.obsidian.core.database.orm.query;

import com.obsidian.core.database.orm.query.clause.JoinClause;
import com.obsidian.core.database.orm.query.grammar.Grammar;

import java.util.*;

/**
 * Aggregate query helpers for QueryBuilder.
 */
final class QueryAggregates
{
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of("COUNT", "MAX", "MIN", "SUM", "AVG");

    private QueryAggregates() {}

    /**
     * Returns the count of matching rows.
     *
     * @param qb       builder supplying WHERE/JOIN context
     * @param executor JDBC executor
     * @param grammar  SQL grammar for WHERE compilation
     * @param column   * for COUNT(*) or a validated column name
     * @return count, or 0 if null
     */
    static long count(QueryBuilder qb, QueryExecutor executor, Grammar grammar, String column) {
        Object val = aggregate(qb, executor, grammar, "COUNT", column);
        return val instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * Executes an aggregate function and returns the raw result.
     *
     * @param qb       builder supplying WHERE/JOIN context
     * @param executor JDBC executor
     * @param grammar  SQL grammar for WHERE compilation
     * @param function one of COUNT, MAX, MIN, SUM, AVG
     * @param column   target column (pre-validated by caller; * allowed for COUNT)
     * @return aggregate value, or null if no rows matched
     * @throws IllegalArgumentException if function is not in the allowed whitelist
     */
    static Object aggregate(QueryBuilder qb, QueryExecutor executor, Grammar grammar, String function, String column)
    {
        if (!ALLOWED_FUNCTIONS.contains(function.toUpperCase())) {
            throw new IllegalArgumentException("Aggregate function not allowed: \"" + function + "\". " + "Allowed: " + ALLOWED_FUNCTIONS);
        }

        String alias = function.toLowerCase() + "_result";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(function).append("(").append(column).append(") AS ").append(alias);
        sql.append(" FROM ").append(qb.getTable());

        for (JoinClause join : qb.getJoins()) sql.append(" ").append(join.toSql());

        String whereClause = grammar.compileWheres(qb.getWheres());
        if (!whereClause.isEmpty()) sql.append(" WHERE ").append(whereClause);

        if (!qb.getGroups().isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", qb.getGroups()));
        }

        List<Map<String, Object>> rows = executor.executeQuery(
                sql.toString(), new ArrayList<>(qb.getBindings()));

        if (rows.isEmpty()) return null;
        return rows.get(0).get(alias);
    }
}