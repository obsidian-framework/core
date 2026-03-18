package com.obsidian.core.routing.pebble;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Pebble extension for route URL generation.
 * Registers the route() function for generating URLs from named routes.
 */
public class RouteExtension extends AbstractExtension
{
    /**
     * Registers route function.
     *
     * @return Map of function name to implementation
     */
    @Override
    public Map<String, Function> getFunctions()
    {
        Map<String, Function> functions = new HashMap<>();
        functions.put("route", new RouteFunction());
        return functions;
    }
}