package com.obsidian.core.database.orm.query.grammar;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.SqlIdentifier;
import com.obsidian.core.database.orm.query.clause.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL/MariaDB SQL grammar. Also used as the default fallback.
 */
public class MySqlGrammar implements Grammar
{
    @Override
    public String compileSelect(QueryBuilder query)
    {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ");
        if (query.isDistinct()) {
            sql.append("DISTINCT ");
        }

        if (query.getColumns().isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", query.getColumns()));
        }

        sql.append(" FROM ").append(query.getTable());

        for (JoinClause join : query.getJoins()) {
            sql.append(" ").append(join.toSql());
        }

        String whereClause = compileWheres(query.getWheres());
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        if (!query.getGroups().isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", query.getGroups()));
        }

        if (!query.getHavings().isEmpty()) {
            sql.append(" HAVING ").append(compileHavings(query.getHavings()));
        }

        if (!query.getOrders().isEmpty()) {
            sql.append(" ORDER BY ");
            sql.append(query.getOrders().stream()
                    .map(OrderClause::toSql)
                    .collect(Collectors.joining(", ")));
        }

        if (query.getLimitValue() != null) {
            sql.append(" LIMIT ").append(query.getLimitValue());
        }

        if (query.getOffsetValue() != null) {
            sql.append(" OFFSET ").append(query.getOffsetValue());
        }

        return sql.toString();
    }

    @Override
    public String compileWheres(List<WhereClause> wheres)
    {
        if (wheres.isEmpty()) return "";

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < wheres.size(); i++) {
            WhereClause where = wheres.get(i);
            String compiled = compileWhere(where);
            parts.add(i == 0 ? compiled : where.getBoolean() + " " + compiled);
        }

        return String.join(" ", parts);
    }

    private String compileWhere(WhereClause where)
    {
        switch (where.getType()) {
            case BASIC:
                return where.getColumn() + " " + where.getOperator() + " ?";
            case NULL:
                return where.getColumn() + " IS NULL";
            case NOT_NULL:
                return where.getColumn() + " IS NOT NULL";
            case IN:
                String placeholders = where.getValues().stream().map(v -> "?").collect(Collectors.joining(", "));
                return where.getColumn() + " IN (" + placeholders + ")";
            case NOT_IN:
                String notInPlaceholders = where.getValues().stream().map(v -> "?").collect(Collectors.joining(", "));
                return where.getColumn() + " NOT IN (" + notInPlaceholders + ")";
            case BETWEEN:
                return where.getColumn() + " BETWEEN ? AND ?";
            case NESTED:
                return "(" + compileWheres(where.getNested().getWheres()) + ")";
            case RAW:
                return where.getRawSql();
            default:
                throw new IllegalArgumentException("Unknown where type: " + where.getType());
        }
    }

    private String compileHavings(List<HavingClause> havings)
    {
        return havings.stream()
                .map(h -> h.isRaw() ? h.getRawSql() : h.getColumn() + " " + h.getOperator() + " ?")
                .collect(Collectors.joining(" AND "));
    }

    /**
     * Compiles an INSERT statement.
     * Validates all column names against {@link SqlIdentifier}.
     *
     * @param table  target table name
     * @param values column-to-value map
     * @return compiled {@link InsertResult}
     * @throws IllegalArgumentException if any column name fails identifier validation
     */
    @Override
    public InsertResult compileInsert(String table, Map<String, Object> values)
    {
        List<String> columns = new ArrayList<>(values.keySet());
        List<Object> bindings = new ArrayList<>(values.values());

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
     * Validates all column names against {@link SqlIdentifier}.
     *
     * @param table            target table name
     * @param values           column-to-value map for the SET clause
     * @param wheres           WHERE clauses to apply
     * @param existingBindings bindings already collected for the WHERE clause
     * @return compiled {@link UpdateResult}
     * @throws IllegalArgumentException if any column name fails identifier validation
     */
    @Override
    public UpdateResult compileUpdate(String table, Map<String, Object> values, List<WhereClause> wheres, List<Object> existingBindings)
    {
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

    /**
     * Compiles a DELETE statement.
     *
     * @param table    target table name
     * @param wheres   WHERE clauses to apply
     * @param bindings bindings already collected for the WHERE clause
     * @return compiled {@link DeleteResult}
     */
    @Override
    public DeleteResult compileDelete(String table, List<WhereClause> wheres, List<Object> bindings)
    {
        StringBuilder sql = new StringBuilder("DELETE FROM " + table);

        String whereClause = compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        return new DeleteResult(sql.toString(), new ArrayList<>(bindings));
    }

    /**
     * Compiles a MySQL upsert using {@code INSERT ... ON DUPLICATE KEY UPDATE}.
     * Validates all column names against {@link SqlIdentifier}.
     *
     * @param table      target table name
     * @param rows       list of rows to insert or update; all rows must share the same key set
     * @param uniqueKeys columns identifying uniqueness (used to exclude from the UPDATE clause)
     * @param updateKeys columns to update on conflict; if empty, all non-unique columns are updated
     * @return compiled {@link InsertResult}
     * @throws IllegalArgumentException if {@code rows} is empty or a column name fails identifier validation
     */
    @Override
    public InsertResult compileUpsert(String table, List<Map<String, Object>> rows, List<String> uniqueKeys, List<String> updateKeys)
    {
        if (rows.isEmpty()) throw new IllegalArgumentException("rows cannot be empty");

        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        for (String col : columns) SqlIdentifier.requireIdentifier(col);

        String cols = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

        // Build VALUES (?, ?), (?, ?) ...
        List<Object> bindings = new ArrayList<>();
        List<String> valueSets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            valueSets.add("(" + placeholders + ")");
            for (String col : columns) bindings.add(row.get(col));
        }

        // ON DUPLICATE KEY UPDATE col = VALUES(col), ...
        List<String> updates = updateKeys.isEmpty()
                ? columns.stream()
                .filter(c -> !uniqueKeys.contains(c))
                .map(c -> c + " = VALUES(" + c + ")")
                .collect(Collectors.toList())
                : updateKeys.stream()
                .map(c -> c + " = VALUES(" + c + ")")
                .collect(Collectors.toList());

        String sql = "INSERT INTO " + table + " (" + cols + ") VALUES "
                + String.join(", ", valueSets)
                + " ON DUPLICATE KEY UPDATE " + String.join(", ", updates);

        return new InsertResult(sql, bindings);
    }

    @Override
    public String compileIncrement(String table, String column, int amount, List<WhereClause> wheres, List<Object> bindings)
    {
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