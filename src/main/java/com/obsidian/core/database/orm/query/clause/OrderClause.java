package com.obsidian.core.database.orm.query.clause;

public class OrderClause {

    private final String column;
    private final String direction;

    /**
     * Creates a new OrderClause instance.
     *
     * @param column The column name
     * @param direction The sort direction ("ASC" or "DESC")
     */
    public OrderClause(String column, String direction) {
        this.column = column;
        this.direction = direction;
    }

    /**
     * Returns the column.
     *
     * @return The column
     */
    public String getColumn()    { return column; }
    /**
     * Returns the direction.
     *
     * @return The direction
     */
    public String getDirection() { return direction; }

    /**
     * Returns the compiled SQL string without executing.
     *
     * @return The string value
     */
    public String toSql() {
        return column + " " + direction;
    }
}
