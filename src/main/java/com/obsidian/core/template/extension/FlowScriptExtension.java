package com.obsidian.core.template.extension;

import io.pebbletemplates.pebble.extension.AbstractExtension;

import java.util.HashMap;
import java.util.Map;

/**
 * Pebble extension for Flow scripts.
 * Provides flow_scripts global variable.
 */
public class FlowScriptExtension extends AbstractExtension
{
    @Override
    public Map<String, Object> getGlobalVariables()
    {
        Map<String, Object> globals = new HashMap<>();
        String env = System.getenv("ENVIRONMENT");
        String version = "production".equalsIgnoreCase(env) ? "1.0.0" : String.valueOf(System.currentTimeMillis());

        String scriptTag = "<script src=\"/obsidian/flow.js?v=" + version + "\"></script>";
        globals.put("flow_scripts", scriptTag);

        return globals;
    }
}