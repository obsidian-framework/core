package com.obsidian.core.core;

/**
 * Constant keys for environment variables used throughout the framework.
 * Centralizes .env key definitions to prevent typos and ease refactoring.
 */
public final class EnvKeys
{
    private EnvKeys() {}

    // Core
    public static final String ENVIRONMENT = "ENVIRONMENT";
    public static final String PORT_WEB    = "PORT_WEB";
    public static final String SITE_URL    = "SITE_URL";

    // Database
    public static final String DB_TYPE     = "DB_TYPE";
    public static final String DB_PATH     = "DB_PATH";
    public static final String DB_HOST     = "DB_HOST";
    public static final String DB_PORT     = "DB_PORT";
    public static final String DB_NAME     = "DB_NAME";
    public static final String DB_USER     = "DB_USER";
    public static final String DB_PASSWORD = "DB_PASSWORD";
    public static final String DB_SSL      = "DB_SSL";

    // Cache
    public static final String CACHE_DRIVER = "CACHE_DRIVER";

    // Storage
    public static final String STORAGE_LOCAL_ROOT = "STORAGE_LOCAL_ROOT";
    public static final String STORAGE_LOCAL_URL  = "STORAGE_LOCAL_URL";
    public static final String STORAGE_DISK       = "STORAGE_DISK";
}