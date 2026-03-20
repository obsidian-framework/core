package com.obsidian.core.database.orm.query.grammar;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.SqlIdentifier;
import com.obsidian.core.database.orm.query.clause.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL/MariaDB SQL grammar. Also used for SQLite for most operations.
 */
public class MySqlGrammar implements Grammar {

    @Override
    public String compileSelect(QueryBuilder query) {
        StringBuilder sql = new StringBuilder();

        // SELECT
        sql.append("SELECT ");
        if (query.isDistinct()) {
            sql.append("DISTINCT ");
        }

        if (query.getColumns().isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", query.getColumns()));
        }

        // FROM
        sql.append(" FROM ").append(query.getTable());

        // JOINS
        for (JoinClause join : query.getJoins()) {
            sql.append(" ").append(join.toSql());
        }

        // WHERE
        String whereClause = compileWheres(query.getWheres());
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        // GROUP BY
        if (!query.getGroups().isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", query.getGroups()));
        }

        // HAVING
        if (!query.getHavings().isEmpty()) {
            sql.append(" HAVING ").append(compileHavings(query.getHavings()));
        }

        // ORDER BY
        if (!query.getOrders().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(query.getOrders().stream()
                    .map(OrderClause::toSql)
                    .collect(Collectors.joining(", ")));
        }

        // LIMIT
        if (query.getLimitValue() != null) {
            sql.append(" LIMIT ").append(query.getLimitValue());
        }

        // OFFSET
        if (query.getOffsetValue() != null) {
            sql.append(" OFFSET ").append(query.getOffsetValue());
        }

        return sql.toString();
    }

    @Override
    public String compileWheres(List<WhereClause> wheres) {
        if (wheres.isEmpty()) return "";

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < wheres.size(); i++) {
            WhereClause where = wheres.get(i);
            String compiled = compileWhere(where);

            if (i == 0) {
                parts.add(compiled);
            } else {
                parts.add(where.getBoolean() + " " + compiled);
            }
        }

        return String.join(" ", parts);
    }

    private String compileWhere(WhereClause where) {
        switch (where.getType()) {
            case BASIC:
                return where.getColumn() + " " + where.getOperator() + " ?";

            case NULL:
                return where.getColumn() + " IS NULL";

            case NOT_NULL:
                return where.getColumn() + " IS NOT NULL";

            case IN:
                String placeholders = where.getValues().stream()
                        .map(v -> "?")
                        .collect(Collectors.joining(", "));
                return where.getColumn() + " IN (" + placeholders + ")";

            case NOT_IN:
                String notInPlaceholders = where.getValues().stream()
                        .map(v -> "?")
                        .collect(Collectors.joining(", "));
                return where.getColumn() + " NOT IN (" + notInPlaceholders + ")";

            case BETWEEN:
                return where.getColumn() + " BETWEEN ? AND ?";

            case NESTED:
                String nestedSql = compileWheres(where.getNested().getWheres());
                return "(" + nestedSql + ")";

            case RAW:
                return where.getRawSql();

            default:
                throw new IllegalArgumentException("Unknown where type: " + where.getType());
        }
    }

    private String compileHavings(List<HavingClause> havings) {
        return havings.stream()
                .map(h -> {
                    if (h.isRaw()) return h.getRawSql();
                    return h.getColumn() + " " + h.getOperator() + " ?";
                })
                .collect(Collectors.joining(" AND "));
    }

    /**
     * Compiles an INSERT statement for the given table and column-value map.
     *
     * @param table  the target table name (pre-validated by caller)
     * @param values column-to-value map; keys must be valid SQL identifiers
     * @return a compiled {@link InsertResult} containing SQL and bindings
     * @throws IllegalArgumentException if any column name fails identifier validation
     */
    @Override
    public InsertResult compileInsert(String table, Map<String, Object> values) {
        List<String> columns = new ArrayList<>(values.keySet());
        List<Object> bindings = new ArrayList<>(values.values());

        // Validate every column name — defence-in-depth even though callers
        // are expected to have already passed through QueryBuilder/SqlIdentifier.
        for (String col : columns) {
            SqlIdentifier.requireIdentifier(col);
        }

        String cols = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")";
        return new InsertResult(sql, bindings);
    }

    /**
     * Compiles an UPDATE statement.
     *
     * @param table            the target table name
     * @param values           column-to-value map for SET clause; keys must be valid identifiers
     * @param wheres           compiled WHERE clauses
     * @param existingBindings bindings already collected for the WHERE clause
     * @return a compiled {@link UpdateResult} containing SQL and ordered bindings
     * @throws IllegalArgumentException if any column name fails identifier validation
     */
    @Override
    public UpdateResult compileUpdate(String table, Map<String, Object> values,
                                      List<WhereClause> wheres, List<Object> existingBindings) {
        List<Object> bindings = new ArrayList<>();

        String setClauses = values.entrySet().stream()
                .map(e -> {
                    SqlIdentifier.requireIdentifier(e.getKey());
                    bindings.add(e.getValue());
                    return e.getKey() + " = ?";
                })
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder("UPDATE " + table + " SET " + setClauses);

        String whereClause = compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
            bindings.addAll(existingBindings);
        }

        return new UpdateResult(sql.toString(), bindings);
    }

    @Override
    public DeleteResult compileDelete(String table, List<WhereClause> wheres, List<Object> bindings) {
        StringBuilder sql = new StringBuilder("DELETE FROM " + table);

        String whereClause = compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        return new DeleteResult(sql.toString(), new ArrayList<>(bindings));
    }

    /**
     * Compiles an UPDATE … SET col = col ± n statement for atomic increment/decrement.
     */
    @Override
    public String compileIncrement(String table, String column, int amount,
                                   List<WhereClause> wheres, List<Object> bindings) {
        String op = amount >= 0 ? "+" : "-";
        int absAmount = Math.abs(amount);

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(table)
                .append(" SET ").append(column).append(" = ").append(column)
                .append(" ").append(op).append(" ").append(absAmount);

        String whereClause = compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }
}