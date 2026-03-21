package com.obsidian.core.database.orm.query.clause;

public class OrderClause
{
    private final String column;
    private final String direction;

    /**
     * Creates a new order clause.
     *
     * @param column    column name to order by
     * @param direction sort direction, {@code "ASC"} or {@code "DESC"}
     */
    public OrderClause(String column, String direction)
    {
        this.column = column;
        this.direction = direction;
    }

    /**
     * Returns the column name.
     *
     * @return column name
     */
    public String getColumn()    { return column; }

    /**
     * Returns the sort direction.
     *
     * @return {@code "ASC"} or {@code "DESC"}
     */
    public String getDirection() { return direction; }

    /**
     * Returns the compiled SQL fragment for this order clause.
     *
     * @return SQL order string
     */
    public String toSql() {
        return column + " " + direction;
    }
}