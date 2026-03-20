package com.obsidian.core.database.orm.query.grammar;

/**
 * Factory to resolve the appropriate SQL Grammar based on database type.
 */
public class GrammarFactory {

    private static Grammar instance;

    /**
     * Initialize from database type string (mysql, postgresql, sqlite).
     */
    public static void initialize(String dbType) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "mariadb":
                instance = new MySqlGrammar();
                break;
            case "postgresql":
            case "postgres":
                instance = new PostgresGrammar();
                break;
            case "sqlite":
                instance = new SQLiteGrammar();
                break;
            default:
                instance = new MySqlGrammar();
        }
    }

    /**
     * Get the current grammar instance.
     */
    public static Grammar get() {
        if (instance == null) {
            instance = new MySqlGrammar();
        }
        return instance;
    }

    /**
     * Set a custom grammar.
     */
    public static void set(Grammar grammar) {
        instance = grammar;
    }
}
