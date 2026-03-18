package com.obsidian.core.security;

/**
 * Constant keys used in Spark session attributes.
 * Centralizes session key definitions to prevent typos and ease refactoring.
 */
public final class SessionKeys
{
    private SessionKeys() {}

    public static final String LOGGED = "logged";
    public static final String USER_ID = "user_id";
    public static final String USERNAME = "username";
    public static final String ROLE = "role";
    public static final String FLASH_ERROR = "flash_error";
}