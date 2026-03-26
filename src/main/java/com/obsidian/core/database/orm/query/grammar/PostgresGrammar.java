package com.obsidian.core.database.orm.query.grammar;

import com.obsidian.core.database.orm.query.QueryBuilder;
import com.obsidian.core.database.orm.query.SqlIdentifier;
import com.obsidian.core.database.orm.query.clause.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL SQL grammar.
 */
public class PostgresGrammar implements Grammar
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

        sql.append(" FROM ").append(quoteTable(query.getTable()));

        for (JoinClause join : query.getJoins()) {
            sql.append(" ").append(compileJoin(join));
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

    private String compileJoin(JoinClause join)
    {
        if ("CROSS".equals(join.getType())) {
            return join.getType() + " JOIN " + quoteTable(join.getTable());
        }
        return join.getType() + " JOIN " + quoteTable(join.getTable())
                + " ON " + join.getFirst() + " " + join.getOperator() + " " + join.getSecond();
    }

    /**
     * Compiles a PostgreSQL INSERT with {@code RETURNING id} for key retrieval.
     *
     * @param table  target table name
     * @param values column-to-value map
     * @return compiled {@link InsertResult}
     */
    @Override
    public InsertResult compileInsert(String table, Map<String, Object> values)
    {
        List<String> columns = new ArrayList<>(values.keySet());
        List<Object> bindings = new ArrayList<>(values.values());

        String cols = columns.stream().map(this::quoteColumn).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + quoteTable(table)
                + " (" + cols + ") VALUES (" + placeholders + ") RETURNING id";

        return new InsertResult(sql, bindings);
    }

    /**
     * Compiles a PostgreSQL UPDATE statement.
     *
     * @param table            target table name
     * @param values           column-to-value map for the SET clause
     * @param wheres           WHERE clauses to apply
     * @param existingBindings bindings already collected for the WHERE clause
     * @return compiled {@link UpdateResult}
     */
    @Override
    public UpdateResult compileUpdate(String table, Map<String, Object> values, List<WhereClause> wheres, List<Object> existingBindings)
    {
        List<Object> bindings = new ArrayList<>();

        String setClauses = values.entrySet().stream()
                .map(e -> {
                    bindings.add(e.getValue());
                    return quoteColumn(e.getKey()) + " = ?";
                })
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder("UPDATE " + quoteTable(table) + " SET " + setClauses);

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
        StringBuilder sql = new StringBuilder("DELETE FROM " + quoteTable(table));

        String whereClause = compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        return new DeleteResult(sql.toString(), new ArrayList<>(bindings));
    }

    /**
     * Compiles a PostgreSQL upsert using {@code INSERT ... ON CONFLICT (...) DO UPDATE SET}.
     *
     * @param table      target table name
     * @param rows       list of rows to insert or update; all rows must share the same key set
     * @param uniqueKeys columns identifying the conflict target; must not be empty
     * @param updateKeys columns to update on conflict; if empty, all non-unique columns are updated
     * @return compiled {@link InsertResult}
     * @throws IllegalArgumentException if {@code rows} or {@code uniqueKeys} is empty
     */
    @Override
    public InsertResult compileUpsert(String table, List<Map<String, Object>> rows, List<String> uniqueKeys, List<String> updateKeys)
    {
        if (rows.isEmpty()) throw new IllegalArgumentException("rows cannot be empty");
        if (uniqueKeys.isEmpty()) throw new IllegalArgumentException("uniqueKeys required for PostgreSQL upsert");

        List<String> columns = new ArrayList<>(rows.get(0).keySet());

        String cols = columns.stream().map(this::quoteColumn).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String conflictTarget = uniqueKeys.stream().map(this::quoteColumn).collect(Collectors.joining(", "));

        List<Object> bindings = new ArrayList<>();
        List<String> valueSets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            valueSets.add("(" + placeholders + ")");
            for (String col : columns) bindings.add(row.get(col));
        }

        List<String> updates = updateKeys.isEmpty()
                ? columns.stream()
                .filter(c -> !uniqueKeys.contains(c))
                .map(c -> quoteColumn(c) + " = EXCLUDED." + quoteColumn(c))
                .collect(Collectors.toList())
                : updateKeys.stream()
                .map(c -> quoteColumn(c) + " = EXCLUDED." + quoteColumn(c))
                .collect(Collectors.toList());

        String sql = "INSERT INTO " + quoteTable(table) + " (" + cols + ") VALUES "
                + String.join(", ", valueSets)
                + " ON CONFLICT (" + conflictTarget + ") DO UPDATE SET "
                + String.join(", ", updates);

        return new InsertResult(sql, bindings);
    }

    @Override
    public String compileIncrement(String table, String column, int amount, List<WhereClause> wheres, List<Object> bindings)
    {
        String op = amount >= 0 ? "+" : "-";
        int absAmount = Math.abs(amount);

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(quoteTable(table))
                .append(" SET ").append(quoteColumn(column))
                .append(" = ").append(quoteColumn(column))
                .append(" ").append(op).append(" ").append(absAmount);

        String whereClause = compileWheres(wheres);
        if (!whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        return sql.toString();
    }

    private String quoteTable(String table)
    {
        if (table.contains(".")) {
            return Arrays.stream(table.split("\\."))
                    .map(this::quoteIdentifier)
                    .collect(Collectors.joining("."));
        }
        return quoteIdentifier(table);
    }

    private String quoteColumn(String column)
    {
        if (column.contains(".")) {
            return Arrays.stream(column.split("\\."))
                    .map(this::quoteIdentifier)
                    .collect(Collectors.joining("."));
        }
        return quoteIdentifier(column);
    }

    /**
     * Quotes a single SQL identifier with double quotes.
     * Wildcard {@code *} is returned as-is.
     *
     * @param identifier unquoted identifier part
     * @return double-quoted identifier
     * @throws IllegalArgumentException if the identifier fails validation
     */
    private String quoteIdentifier(String identifier)
    {
        if (identifier.equals("*")) {
            return identifier;
        }
        SqlIdentifier.requireIdentifier(identifier);
        return "\"" + identifier + "\"";
    }
}