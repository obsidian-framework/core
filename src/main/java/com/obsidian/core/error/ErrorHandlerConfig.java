package com.obsidian.core.error;

/**
 * Configuration helper for error handler.
 * Sets debug mode based on environment.
 */
public class ErrorHandlerConfig
{
    /**
     * Configures error handler based on environment flag.
     *
     * @param isProduction true for production mode, false for debug mode
     */
    public static void configure(boolean isProduction) {
        ErrorHandler.setDebugMode(!isProduction);
    }

    /**
     * Configures error handler from APP_ENV environment variable.
     * Sets production mode if APP_ENV=production.
     */
    public static void configureFromEnv()
    {
        String env = System.getenv("APP_ENV");
        boolean isProduction = "production".equalsIgnoreCase(env);
        ErrorHandler.setDebugMode(!isProduction);
    }
}