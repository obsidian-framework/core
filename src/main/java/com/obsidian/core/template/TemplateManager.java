package com.obsidian.core.template;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template manager for global template access and variables.
 * Provides singleton template engine and global variable storage.
 */
public class TemplateManager
{
    /** Singleton template engine instance */
    private static final PebbleTemplateEngine engine = new PebbleTemplateEngine();

    /** Global template variables */
    private static final Map<String, Object> globals = new ConcurrentHashMap<>();

    /**
     * Gets template engine instance.
     *
     * @return Pebble template engine
     */
    public static PebbleTemplateEngine get() {
        return engine;
    }

    /**
     * Gets global template variables.
     *
     * @return Map of global variables
     */
    public static Map<String, Object> getGlobals() {
        return globals;
    }

    /**
     * Sets a global template variable.
     *
     * @param key Variable name
     * @param value Variable value
     */
    public static void setGlobal(String key, Object value) {
        globals.put(key, value);
    }
}