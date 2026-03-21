package com.obsidian.core.database.orm.query.clause;

public class JoinClause
{
    private final String type;
    private final String table;
    private final String first;
    private final String operator;
    private final String second;

    /**
     * Creates a new join clause.
     *
     * @param type     join type, e.g. {@code INNER}, {@code LEFT}, {@code RIGHT}, {@code CROSS}
     * @param table    table to join
     * @param first    first column in the join condition
     * @param operator comparison operator
     * @param second   second column in the join condition
     */
    public JoinClause(String type, String table, String first, String operator, String second)
    {
        this.type = type;
        this.table = table;
        this.first = first;
        this.operator = operator;
        this.second = second;
    }

    /**
     * Returns the join type.
     *
     * @return join type string
     */
    public String getType()     { return type; }

    /**
     * Returns the table name.
     *
     * @return table name
     */
    public String getTable()    { return table; }

    /**
     * Returns the first column in the join condition.
     *
     * @return first column name
     */
    public String getFirst()    { return first; }

    /**
     * Returns the comparison operator.
     *
     * @return operator string
     */
    public String getOperator() { return operator; }

    /**
     * Returns the second column in the join condition.
     *
     * @return second column name
     */
    public String getSecond()   { return second; }

    /**
     * Returns the compiled SQL fragment for this join clause.
     *
     * @return SQL join string
     */
    public String toSql() {
        if ("CROSS".equals(type)) {
            return type + " JOIN " + table;
        }
        return type + " JOIN " + table + " ON " + first + " " + operator + " " + second;
    }
}