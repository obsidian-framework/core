package com.obsidian.core.database.orm.query.clause;

public class HavingClause
{
    private final String column;
    private final String operator;
    private final Object value;
    private final boolean isRaw;
    private final String rawSql;

    /**
     * Creates a standard HAVING clause with a column, operator, and value.
     *
     * @param column   column name
     * @param operator comparison operator
     * @param value    value to compare against
     */
    public HavingClause(String column, String operator, Object value)
    {
        this.column = column;
        this.operator = operator;
        this.value = value;
        this.isRaw = false;
        this.rawSql = null;
    }

    private HavingClause(String rawSql)
    {
        this.column = null;
        this.operator = null;
        this.value = null;
        this.isRaw = true;
        this.rawSql = rawSql;
    }

    /**
     * Creates a raw HAVING clause from a SQL string.
     *
     * @param sql raw SQL expression
     * @return a raw {@link HavingClause}
     */
    public static HavingClause raw(String sql) {
        return new HavingClause(sql);
    }

    /**
     * Returns the column name.
     *
     * @return column name, or {@code null} for raw clauses
     */
    public String getColumn()   { return column; }

    /**
     * Returns the comparison operator.
     *
     * @return operator, or {@code null} for raw clauses
     */
    public String getOperator() { return operator; }

    /**
     * Returns the value to compare against.
     *
     * @return value, or {@code null} for raw clauses
     */
    public Object getValue()    { return value; }

    /**
     * Returns {@code true} if this is a raw SQL clause.
     *
     * @return {@code true} if raw
     */
    public boolean isRaw()      { return isRaw; }

    /**
     * Returns the raw SQL string, or {@code null} for standard clauses.
     *
     * @return raw SQL string, or {@code null}
     */
    public String getRawSql()   { return rawSql; }
}