package com.obsidian.core.database.orm.query.clause;

/**
 * Represents a raw SQL expression that should not be quoted or escaped.
 */
public class RawExpression
{
    private final String expression;

    /**
     * Creates a new raw SQL expression.
     *
     * @param expression raw SQL string
     */
    public RawExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Returns the raw SQL expression string.
     *
     * @return raw SQL string
     */
    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}