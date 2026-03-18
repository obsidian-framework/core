package com.obsidian.core.flash.pebble;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;

import java.util.HashMap;
import java.util.Map;

/**
 * Pebble extension for flash notifications.
 * Registers the flash() template function.
 */
public class FlashExtension extends AbstractExtension
{
    /**
     * Registers flash function for use in templates.
     *
     * @return Map of function name to implementation
     */
    @Override
    public Map<String, Function> getFunctions()
    {
        Map<String, Function> functions = new HashMap<>();
        functions.put("flash", new FlashFunction());
        return functions;
    }
}