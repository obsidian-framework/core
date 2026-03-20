package com.obsidian.core.database.orm.query.clause;

import com.obsidian.core.database.orm.query.QueryBuilder;

import java.util.List;

public class WhereClause {

    public enum Type {
        BASIC, NULL, NOT_NULL, IN, NOT_IN, BETWEEN, NESTED, RAW
    }

    private final Type type;
    private final String column;
    private final String operator;
    private final Object value;
    private final String boolean_; // "AND" or "OR"

    // For IN / NOT_IN
    private List<?> values;

    // For BETWEEN
    private Object low;
    private Object high;

    // For NESTED
    private QueryBuilder nested;

    // For RAW
    private String rawSql;

    /**
     * Creates a new WhereClause instance.
     *
     * @param column The column name
     * @param operator The comparison operator (=, !=, >, <, >=, <=, LIKE, etc.)
     * @param value The value to compare against
     * @param bool The boolean connector (AND or OR)
     */
    public WhereClause(String column, String operator, Object value, String bool) {
        this.type = Type.BASIC;
        this.column = column;
        this.operator = operator;
        this.value = value;
        this.boolean_ = bool;
    }

    private WhereClause(Type type, String column, String bool) {
        this.type = type;
        this.column = column;
        this.operator = null;
        this.value = null;
        this.boolean_ = bool;
    }

    /**
     * Is Null.
     *
     * @param column The column name
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause isNull(String column, String bool) {
        return new WhereClause(Type.NULL, column, bool);
    }

    /**
     * Is Not Null.
     *
     * @param column The column name
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause isNotNull(String column, String bool) {
        return new WhereClause(Type.NOT_NULL, column, bool);
    }

    /**
     * In.
     *
     * @param column The column name
     * @param values The list of values
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause in(String column, List<?> values, String bool) {
        WhereClause clause = new WhereClause(Type.IN, column, bool);
        clause.values = values;
        return clause;
    }

    /**
     * Not In.
     *
     * @param column The column name
     * @param values The list of values
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause notIn(String column, List<?> values, String bool) {
        WhereClause clause = new WhereClause(Type.NOT_IN, column, bool);
        clause.values = values;
        return clause;
    }

    /**
     * Between.
     *
     * @param column The column name
     * @param low The lower bound of the range
     * @param high The upper bound of the range
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause between(String column, Object low, Object high, String bool) {
        WhereClause clause = new WhereClause(Type.BETWEEN, column, bool);
        clause.low = low;
        clause.high = high;
        return clause;
    }

    /**
     * Nested.
     *
     * @param nestedQuery The nested query
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause nested(QueryBuilder nestedQuery, String bool) {
        WhereClause clause = new WhereClause(Type.NESTED, null, bool);
        clause.nested = nestedQuery;
        return clause;
    }

    /**
     * Raw.
     *
     * @param sql Raw SQL string
     * @param bool The boolean connector (AND or OR)
     * @return This instance for method chaining
     */
    public static WhereClause raw(String sql, String bool) {
        WhereClause clause = new WhereClause(Type.RAW, null, bool);
        clause.rawSql = sql;
        return clause;
    }

    // ─── Getters ─────────────────────────────────────────────

    /**
     * Returns the type.
     *
     * @return The type
     */
    public Type getType()              { return type; }
    /**
     * Returns the column.
     *
     * @return The column
     */
    public String getColumn()          { return column; }
    /**
     * Returns the operator.
     *
     * @return The operator
     */
    public String getOperator()        { return operator; }
    /**
     * Returns the value.
     *
     * @return The value
     */
    public Object getValue()           { return value; }
    /**
     * Returns the boolean.
     *
     * @return The boolean
     */
    public String getBoolean()         { return boolean_; }
    /**
     * Returns the values.
     *
     * @return The values
     */
    public List<?> getValues()         { return values; }
    /**
     * Returns the low.
     *
     * @return The low
     */
    public Object getLow()             { return low; }
    /**
     * Returns the high.
     *
     * @return The high
     */
    public Object getHigh()            { return high; }
    /**
     * Returns the nested.
     *
     * @return The nested
     */
    public QueryBuilder getNested()    { return nested; }
    /**
     * Returns the raw sql.
     *
     * @return The raw sql
     */
    public String getRawSql()          { return rawSql; }
}
