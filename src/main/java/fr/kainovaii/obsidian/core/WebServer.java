package fr.kainovaii.obsidian.core;

import fr.kainovaii.obsidian.error.ErrorHandler;
import fr.kainovaii.obsidian.livecomponents.http.LiveComponentsScriptRoute;
import fr.kainovaii.obsidian.security.role.RoleChecker;
import fr.kainovaii.obsidian.http.controller.ControllerLoader;
import fr.kainovaii.obsidian.realtime.websocket.WebSocketLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

import static fr.kainovaii.obsidian.http.controller.BaseController.*;
import static fr.kainovaii.obsidian.template.TemplateManager.setGlobal;
import static spark.Spark.*;

/**
 * Web server configuration and initialization.
 * Configures Spark framework with routes, WebSockets, middleware and exception handling.
 */
public class WebServer
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    /**
     * Starts and configures the web server.
     * Sets up IP, port, static files, WebSockets, middleware, exception handling and routes.
     */
    public void start()
    {
        ipAddress("0.0.0.0");
        port(Obsidian.getWebPort());
        staticFiles.location("/");

        logger.info("Loading WebSocket handlers...");
        WebSocketLoader.registerWebSockets();

        logger.info("Initializing Spark...");
        get("/obsidian/livecomponents.js", new LiveComponentsScriptRoute());

        // Global exception handler
        exception(Exception.class, (e, req, res) -> {
            res.body(ErrorHandler.handle(e, req, res));
        });

        // Request preprocessing middleware
        before((req, res) ->
        {
            setGlobal("request", req);
            setGlobal("response", res);
            setGlobal("isLogged", isLogged(req));
            if (isLogged(req)) setGlobal("loggedUser", getLoggedUser(req));
            Map<String, String> flashes = collectFlashes(req);
            setGlobal("flashes", flashes);

            ControllerLoader.loadAdvicesControllers(req, res);
            RoleChecker.checkAccess(req, res);
        });

        ControllerLoader.loadControllers();

        init();

        logger.info("Web server started on port {}", Obsidian.getWebPort());
    }
}