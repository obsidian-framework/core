package fr.kainovaii.core.web.error;

public class ErrorHandlerConfig
{
    public static void configure(boolean isProduction) {
        ErrorHandler.setDebugMode(!isProduction);
    }

    public static void configureFromEnv()
    {
        String env = System.getenv("APP_ENV");
        boolean isProduction = "production".equalsIgnoreCase(env);
        ErrorHandler.setDebugMode(!isProduction);
    }
}

