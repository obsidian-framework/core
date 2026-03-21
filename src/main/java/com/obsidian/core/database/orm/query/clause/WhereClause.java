package com.obsidian.core.database.orm.query.clause;

import com.obsidian.core.database.orm.query.QueryBuilder;

import java.util.List;

public class WhereClause
{
    public enum Type {
        BASIC, NULL, NOT_NULL, IN, NOT_IN, BETWEEN, NESTED, RAW
    }

    private final Type type;
    private final String column;
    private final String operator;
    private final Object value;
    private final String boolean_;

    private List<?> values;
    private Object low;
    private Object high;
    private QueryBuilder nested;
    private String rawSql;

    /**
     * Creates a basic WHERE clause with a column, operator, value, and boolean connector.
     *
     * @param column   column name
     * @param operator comparison operator
     * @param value    value to compare against
     * @param bool     boolean connector, {@code "AND"} or {@code "OR"}
     */
    public WhereClause(String column, String operator, Object value, String bool)
    {
        this.type = Type.BASIC;
        this.column = column;
        this.operator = operator;
        this.value = value;
        this.boolean_ = bool;
    }

    private WhereClause(Type type, String column, String bool)
    {
        this.type = type;
        this.column = column;
        this.operator = null;
        this.value = null;
        this.boolean_ = bool;
    }

    /**
     * Creates a {@code WHERE column IS NULL} clause.
     *
     * @param column column name
     * @param bool   boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause isNull(String column, String bool) {
        return new WhereClause(Type.NULL, column, bool);
    }

    /**
     * Creates a {@code WHERE column IS NOT NULL} clause.
     *
     * @param column column name
     * @param bool   boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause isNotNull(String column, String bool) {
        return new WhereClause(Type.NOT_NULL, column, bool);
    }

    /**
     * Creates a {@code WHERE column IN (...)} clause.
     *
     * @param column column name
     * @param values list of allowed values
     * @param bool   boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause in(String column, List<?> values, String bool)
    {
        WhereClause clause = new WhereClause(Type.IN, column, bool);
        clause.values = values;
        return clause;
    }

    /**
     * Creates a {@code WHERE column NOT IN (...)} clause.
     *
     * @param column column name
     * @param values list of excluded values
     * @param bool   boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause notIn(String column, List<?> values, String bool)
    {
        WhereClause clause = new WhereClause(Type.NOT_IN, column, bool);
        clause.values = values;
        return clause;
    }

    /**
     * Creates a {@code WHERE column BETWEEN low AND high} clause.
     *
     * @param column column name
     * @param low    lower bound
     * @param high   upper bound
     * @param bool   boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause between(String column, Object low, Object high, String bool)
    {
        WhereClause clause = new WhereClause(Type.BETWEEN, column, bool);
        clause.low = low;
        clause.high = high;
        return clause;
    }

    /**
     * Creates a nested WHERE group from a sub-query builder.
     *
     * @param nestedQuery nested {@link QueryBuilder} defining the group
     * @param bool        boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause nested(QueryBuilder nestedQuery, String bool)
    {
        WhereClause clause = new WhereClause(Type.NESTED, null, bool);
        clause.nested = nestedQuery;
        return clause;
    }

    /**
     * Creates a raw WHERE clause from a SQL string.
     *
     * @param sql  raw SQL condition
     * @param bool boolean connector, {@code "AND"} or {@code "OR"}
     * @return configured {@link WhereClause}
     */
    public static WhereClause raw(String sql, String bool)
    {
        WhereClause clause = new WhereClause(Type.RAW, null, bool);
        clause.rawSql = sql;
        return clause;
    }

    /**
     * Returns the clause type.
     *
     * @return clause type
     */
    public Type getType() { return type; }

    /**
     * Returns the column name.
     *
     * @return column name, or {@code null} for nested and raw clauses
     */
    public String getColumn() { return column; }

    /**
     * Returns the comparison operator.
     *
     * @return operator, or {@code null} for non-basic clauses
     */
    public String getOperator() { return operator; }

    /**
     * Returns the value to compare against.
     *
     * @return value, or {@code null} for non-basic clauses
     */
    public Object getValue() { return value; }

    /**
     * Returns the boolean connector for this clause.
     *
     * @return {@code "AND"} or {@code "OR"}
     */
    public String getBoolean() { return boolean_; }

    /**
     * Returns the list of values for IN / NOT IN clauses.
     *
     * @return list of values, or {@code null}
     */
    public List<?> getValues() { return values; }

    /**
     * Returns the lower bound for BETWEEN clauses.
     *
     * @return lower bound, or {@code null}
     */
    public Object getLow() { return low; }

    /**
     * Returns the upper bound for BETWEEN clauses.
     *
     * @return upper bound, or {@code null}
     */
    public Object getHigh() { return high; }

    /**
     * Returns the nested query builder for NESTED clauses.
     *
     * @return nested {@link QueryBuilder}, or {@code null}
     */
    public QueryBuilder getNested() { return nested; }

    /**
     * Returns the raw SQL string for RAW clauses.
     *
     * @return raw SQL string, or {@code null}
     */
    public String getRawSql() { return rawSql; }
}