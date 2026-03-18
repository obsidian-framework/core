package com.obsidian.core.core;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Environment configuration loader for Obsidian applications.
 * Handles .env file discovery, creation from template, and variable access.
 * Singleton: loaded once via {@link #load()}, accessed anywhere via {@link #getInstance()}.
 */
public class EnvLoader
{
    private static final Logger logger = LoggerFactory.getLogger(EnvLoader.class);
    private static final String ENV_FILE = ".env";
    private static final String ENV_TEMPLATE = "/env.template";

    private static EnvLoader instance;

    private Dotenv dotenv;
    private final Path workingDirectory;

    /**
     * Creates an EnvLoader with the current working directory.
     */
    public EnvLoader()
    {
        this(Paths.get("").toAbsolutePath());
    }

    /**
     * Creates an EnvLoader with a specific working directory.
     *
     * @param workingDirectory The directory where .env file should be located
     */
    public EnvLoader(Path workingDirectory)
    {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Returns the singleton instance.
     *
     * @return EnvLoader instance
     * @throws IllegalStateException if {@link #load()} has not been called yet
     */
    public static EnvLoader getInstance()
    {
        if (instance == null) {
            throw new IllegalStateException("EnvLoader not initialized. Call load() first.");
        }
        return instance;
    }

    /**
     * Loads environment variables from .env file and stores the singleton instance.
     * No-op if already loaded.
     * Creates the file from template if it doesn't exist.
     *
     * @throws RuntimeException if loading fails
     */
    public void load()
    {
        if (instance != null) return;

        try {
            Path envFile = workingDirectory.resolve(ENV_FILE);

            if (!Files.exists(envFile)) {
                logger.info("No .env file found. Creating from template...");
                copyTemplateEnv(envFile);
            }

            dotenv = Dotenv.configure()
                    .directory(workingDirectory.toString())
                    .filename(ENV_FILE)
                    .ignoreIfMissing()
                    .load();

            instance = this;
            logger.info("Environment configuration loaded successfully");

        } catch (IOException e) {
            throw new RuntimeException("Failed to load environment configuration", e);
        }
    }

    /**
     * Copies the template .env file to the target location.
     *
     * @param targetFile The destination path for the .env file
     * @throws IOException if the copy operation fails
     */
    private void copyTemplateEnv(Path targetFile) throws IOException
    {
        try (InputStream in = getClass().getResourceAsStream(ENV_TEMPLATE)) {
            if (in == null) {
                throw new RuntimeException(
                        "Environment template not found in resources: " + ENV_TEMPLATE
                );
            }
            Files.copy(in, targetFile);
            logger.info("Created .env file from template at: {}", targetFile);
        }
    }

    /**
     * Retrieves an environment variable value.
     *
     * @param key The variable name
     * @return The variable value, or null if not found
     */
    public String get(String key)
    {
        ensureLoaded();
        return dotenv.get(key);
    }

    /**
     * Retrieves an environment variable with a default value.
     *
     * @param key The variable name
     * @param defaultValue The default value if key is not found
     * @return The variable value, or defaultValue if not found
     */
    public String get(String key, String defaultValue)
    {
        ensureLoaded();
        return dotenv.get(key, defaultValue);
    }

    /**
     * Retrieves a required environment variable.
     *
     * @param key The variable name
     * @return The variable value
     * @throws IllegalStateException if the key is not found
     */
    public String getRequired(String key)
    {
        ensureLoaded();
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable not found: " + key
            );
        }
        return value;
    }

    /**
     * Retrieves an environment variable as an integer.
     *
     * @param key The variable name
     * @param defaultValue The default value if key is not found or invalid
     * @return The integer value
     */
    public int getInt(String key, int defaultValue)
    {
        try {
            String value = get(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Retrieves an environment variable as a boolean.
     *
     * @param key The variable name
     * @param defaultValue The default value if key is not found
     * @return The boolean value
     */
    public boolean getBoolean(String key, boolean defaultValue)
    {
        String value = get(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * Checks if the environment has been loaded.
     */
    private void ensureLoaded()
    {
        if (dotenv == null) {
            throw new IllegalStateException(
                    "Environment not loaded. Call load() first."
            );
        }
    }

    /**
     * Gets the working directory path.
     *
     * @return The current working directory
     */
    public Path getWorkingDirectory()
    {
        return workingDirectory;
    }
}