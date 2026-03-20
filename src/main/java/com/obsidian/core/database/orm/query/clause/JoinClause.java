package com.obsidian.core.database.orm.query.clause;

public class JoinClause {

    private final String type;   // INNER, LEFT, RIGHT, CROSS
    private final String table;
    private final String first;
    private final String operator;
    private final String second;

    /**
     * Creates a new JoinClause instance.
     *
     * @param type The type
     * @param table The table name
     * @param first The first column in the join condition
     * @param operator The comparison operator (=, !=, >, <, >=, <=, LIKE, etc.)
     * @param second The second column in the join condition
     */
    public JoinClause(String type, String table, String first, String operator, String second) {
        this.type = type;
        this.table = table;
        this.first = first;
        this.operator = operator;
        this.second = second;
    }

    /**
     * Returns the type.
     *
     * @return The type
     */
    public String getType()     { return type; }
    /**
     * Returns the table.
     *
     * @return The table
     */
    public String getTable()    { return table; }
    /**
     * Returns the first.
     *
     * @return The first
     */
    public String getFirst()    { return first; }
    /**
     * Returns the operator.
     *
     * @return The operator
     */
    public String getOperator() { return operator; }
    /**
     * Returns the second.
     *
     * @return The second
     */
    public String getSecond()   { return second; }

    /**
     * Returns the compiled SQL string without executing.
     *
     * @return The string value
     */
    public String toSql() {
        if ("CROSS".equals(type)) {
            return type + " JOIN " + table;
        }
        return type + " JOIN " + table + " ON " + first + " " + operator + " " + second;
    }
}
