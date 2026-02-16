package fr.kainovaii.obsidian.livecomponents.pebble;

import io.pebbletemplates.pebble.extension.AbstractExtension;

import java.util.HashMap;
import java.util.Map;

/**
 * Pebble extension for LiveComponents scripts.
 * Provides livecomponents_scripts global variable.
 */
public class LiveComponentsScriptExtension extends AbstractExtension
{
    @Override
    public Map<String, Object> getGlobalVariables()
    {
        Map<String, Object> globals = new HashMap<>();
        globals.put("livecomponents_scripts", "<script src=\"/obsidian/livecomponents.js\"></script>");
        return globals;
    }
}