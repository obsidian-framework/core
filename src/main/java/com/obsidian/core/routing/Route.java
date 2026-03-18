package com.obsidian.core.routing;

import java.util.HashMap;
import java.util.Map;

/**
 * Named route registry.
 * Stores mapping between route names and paths for URL generation.
 */
public class Route
{
    /** Map of route names to paths */
    private static final Map<String, String> namedRoutes = new HashMap<>();

    /**
     * Registers a named route.
     *
     * @param name Route name
     * @param path Route path
     */
    public static void registerNamedRoute(String name, String path) {
        if (name != null && !name.isEmpty()) {
            namedRoutes.put(name, path);
        }
    }

    /**
     * Gets path for named route.
     *
     * @param name Route name
     * @return Route path or null if not found
     */
    public static String getPath(String name) {
        return namedRoutes.get(name);
    }

    /**
     * Checks if route name exists.
     *
     * @param name Route name
     * @return true if route exists, false otherwise
     */
    public static boolean hasRoute(String name) {
        return namedRoutes.containsKey(name);
    }

    /**
     * Gets all registered routes.
     *
     * @return Copy of routes map
     */
    public static Map<String, String> getAllRoutes() {
        return new HashMap<>(namedRoutes);
    }
}