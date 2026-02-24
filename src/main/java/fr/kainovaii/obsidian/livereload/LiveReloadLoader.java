package fr.kainovaii.obsidian.livereload;

import fr.kainovaii.obsidian.core.Obsidian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static spark.Spark.get;

/**
 * Bootstraps the live reload system in development mode.
 * Registers the SSE route, starts the file watcher, and provides the Pebble extension.
 *
 * Called automatically from WebServer when ENVIRONMENT=DEV.
 */
public class LiveReloadLoader
{
    private static final Logger logger = LoggerFactory.getLogger(LiveReloadLoader.class);

    private static final List<Path> WATCHED_PATHS = List.of(
        Path.of(System.getProperty("user.dir"), "src", "main", "resources", "view"),
        Path.of(System.getProperty("user.dir"), "src", "main", "resources", "assets")
    );

    private static final LiveReloadBroadcaster broadcaster = new LiveReloadBroadcaster();
    private static LiveReloadScriptExtension scriptExtension;

    /**
     * Starts the live reload system:
     * 1. Registers the SSE route on GET /__obsidian/livereload
     * 2. Starts the file watcher thread
     * 3. Prepares the Pebble extension
     */
    public static void load()
    {
        logger.info("[LiveReload] Development mode detected — starting live reload...");

        // Register SSE route
        get("/__obsidian/livereload", new LiveReloadRoute(broadcaster));

        // Start file watcher
        try {
            FileWatcher watcher = new FileWatcher(WATCHED_PATHS, broadcaster::broadcast);
            Thread watcherThread = new Thread(watcher, "obsidian-livereload-watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
        } catch (IOException e) {
            logger.error("[LiveReload] Failed to start file watcher: {}", e.getMessage());
        }

        // Prepare Pebble extension
        scriptExtension = new LiveReloadScriptExtension();

        logger.info("[LiveReload] Live reload ready. Add {{ obsidianDevTools() }} before </body> in your layout.");
    }

    /**
     * Returns the Pebble extension to register in PebbleTemplateEngine.
     * Returns null if live reload is not active (production).
     */
    public static LiveReloadScriptExtension getScriptExtension()
    {
        return scriptExtension;
    }

    /**
     * Checks if live reload is active.
     */
    public static boolean isActive()
    {
        return scriptExtension != null;
    }
}