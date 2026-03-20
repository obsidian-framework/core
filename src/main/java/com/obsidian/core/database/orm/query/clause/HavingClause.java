package com.obsidian.core.database.orm.query.clause;

public class HavingClause {

    private final String column;
    private final String operator;
    private final Object value;
    private final boolean isRaw;
    private final String rawSql;

    /**
     * Creates a new HavingClause instance.
     *
     * @param column The column name
     * @param operator The comparison operator (=, !=, >, <, >=, <=, LIKE, etc.)
     * @param value The value to compare against
     */
    public HavingClause(String column, String operator, Object value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
        this.isRaw = false;
        this.rawSql = null;
    }

    private HavingClause(String rawSql) {
        this.column = null;
        this.operator = null;
        this.value = null;
        this.isRaw = true;
        this.rawSql = rawSql;
    }

    /**
     * Raw.
     *
     * @param sql Raw SQL string
     * @return This instance for method chaining
     */
    public static HavingClause raw(String sql) {
        return new HavingClause(sql);
    }

    /**
     * Returns the column.
     *
     * @return The column
     */
    public String getColumn()   { return column; }
    /**
     * Returns the operator.
     *
     * @return The operator
     */
    public String getOperator() { return operator; }
    /**
     * Returns the value.
     *
     * @return The value
     */
    public Object getValue()    { return value; }
    /**
     * Is Raw.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean isRaw()      { return isRaw; }
    /**
     * Returns the raw sql.
     *
     * @return The raw sql
     */
    public String getRawSql()   { return rawSql; }
}
