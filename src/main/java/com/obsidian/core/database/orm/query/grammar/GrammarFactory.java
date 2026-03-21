package com.obsidian.core.database.orm.query.grammar;

/**
 * Factory to resolve the appropriate SQL grammar based on database type.
 */
public class GrammarFactory
{
    private static Grammar instance;

    /**
     * Initializes the grammar from a database type string.
     * Accepted values: {@code mysql}, {@code mariadb}, {@code postgresql}, {@code postgres}, {@code sqlite}.
     * Defaults to MySQL if the type is unrecognized.
     *
     * @param dbType database type string
     */
    public static void initialize(String dbType)
    {
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
     * Returns the current grammar instance, defaulting to MySQL if none has been initialized.
     *
     * @return current {@link Grammar} instance
     */
    public static Grammar get()
    {
        if (instance == null) {
            instance = new MySqlGrammar();
        }
        return instance;
    }

    /**
     * Overrides the current grammar with a custom implementation.
     *
     * @param grammar custom grammar instance
     */
    public static void set(Grammar grammar) {
        instance = grammar;
    }
}