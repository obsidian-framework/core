package com.obsidian.core.database.orm.query;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guards against SQL injection through identifier and operator validation.
 */
public final class SqlIdentifier {

    /** Comparison operators safe to interpolate verbatim into SQL. */
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", "<>", "<", ">", "<=", ">=",
            "LIKE", "NOT LIKE", "ILIKE", "NOT ILIKE",
            "IN", "NOT IN", "IS", "IS NOT"
    );

    /**
     * Strict identifier pattern — no bypass for spaces, parentheses, or keywords.
     * Accepted forms:
     * <ul>
     *   <li>{@code column} or {@code table.column} — plain qualified name</li>
     *   <li>{@code table.*} — table-qualified wildcard (JOIN selects)</li>
     *   <li>{@code *} — unqualified wildcard (SELECT *)</li>
     * </ul>
     */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*(?:\\.\\*)?$");

    private SqlIdentifier() {}

    /**
     * Validates a comparison operator against the known-safe whitelist.
     *
     * @param operator the operator string supplied by the caller
     * @throws IllegalArgumentException if the operator is not in the whitelist
     */
    public static void requireOperator(String operator) {
        if (operator == null || !ALLOWED_OPERATORS.contains(operator.toUpperCase())) {
            throw new IllegalArgumentException(
                    "SQL injection guard: operator not allowed: \"" + operator + "\". " +
                            "Allowed: " + ALLOWED_OPERATORS
            );
        }
    }

    /**
     * Validates a SQL identifier (column or table name).
     *
     * @param name the identifier to validate
     * @throws IllegalArgumentException if the name does not match the strict allowlist
     */
    public static void requireIdentifier(String name) {
        if (name == null) {
            throw new IllegalArgumentException("SQL identifier must not be null");
        }
        // Bare wildcard is the only special case — it is structurally unambiguous.
        if (name.equals("*")) {
            return;
        }
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "SQL injection guard: invalid identifier: \"" + name + "\". " +
                            "Identifiers must match [a-zA-Z_][a-zA-Z0-9_.]* or table.*. " +
                            "For raw expressions use selectRaw/whereRaw/havingRaw."
            );
        }
    }

    /**
     * Convenience: validate a direction string for ORDER BY.
     * Only {@code ASC} and {@code DESC} are accepted.
     *
     * @param direction the direction string
     * @throws IllegalArgumentException if it is neither ASC nor DESC
     */
    public static void requireDirection(String direction) {
        if (direction == null) {
            throw new IllegalArgumentException("ORDER BY direction must not be null");
        }
        String upper = direction.toUpperCase();
        if (!upper.equals("ASC") && !upper.equals("DESC")) {
            throw new IllegalArgumentException(
                    "SQL injection guard: ORDER BY direction must be ASC or DESC, got: \"" + direction + "\""
            );
        }
    }
}