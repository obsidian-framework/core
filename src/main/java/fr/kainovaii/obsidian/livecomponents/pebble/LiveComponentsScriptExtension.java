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
        String env = System.getenv("ENVIRONMENT");
        String version = "production".equalsIgnoreCase(env) ? "1.0.0" : String.valueOf(System.currentTimeMillis());

        String scriptTag = "<script src=\"/obsidian/livecomponents.js?v=" + version + "\"></script>\n"
                + "<script src=\"/obsidian/flow.js?v=" + version + "\"></script>";
        globals.put("livecomponents_scripts", scriptTag);

        return globals;
    }
}