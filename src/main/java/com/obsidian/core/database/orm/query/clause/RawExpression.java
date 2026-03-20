package com.obsidian.core.database.orm.query.clause;

/**
 * Represents a raw SQL expression that should not be quoted or escaped.
 */
public class RawExpression {

    private final String expression;

    /**
     * Creates a new RawExpression instance.
     *
     * @param expression A raw SQL expression
     */
    public RawExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Returns the expression.
     *
     * @return The expression
     */
    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
