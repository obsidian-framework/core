package com.obsidian.core.core;

import com.obsidian.core.error.ErrorHandler;
import com.obsidian.core.http.middleware.MiddlewareManager;
import com.obsidian.core.livecomponents.http.LiveComponentsScriptRoute;
import com.obsidian.core.livecomponents.http.ObsidianFlowScriptRoute;
import com.obsidian.core.livecomponents.session.SessionMiddleware;
import com.obsidian.core.livereload.LiveReloadLoader;
import com.obsidian.core.security.role.RoleChecker;
import com.obsidian.core.http.controller.ControllerLoader;
import com.obsidian.core.realtime.websocket.WebSocketLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.obsidian.core.http.controller.BaseController.*;
import static com.obsidian.core.template.TemplateManager.setGlobal;
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
     * Note: WebSocket handlers must be registered before any HTTP route mapping.
     */
    public void start()
    {
        ipAddress("0.0.0.0");
        port(Obsidian.getWebPort());
        staticFiles.location("/");

        logger.info("Loading WebSocket handlers...");
        WebSocketLoader.registerWebSockets();

        if (Obsidian.loadConfigAndEnv().get(EnvKeys.ENVIRONMENT).equalsIgnoreCase("DEV")) {
            LiveReloadLoader.load();
        }

        logger.info("Initializing Spark...");
        get("/obsidian/livecomponents.js", new LiveComponentsScriptRoute());
        get("/obsidian/flow.js", new ObsidianFlowScriptRoute());

        exception(Exception.class, (e, req, res) -> {
            res.body(ErrorHandler.handle(e, req, res));
        });

        before((req, res) ->
        {
            try {
                MiddlewareManager.executeBefore(new Class[0], req, res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Flag prefetch requests — allows controllers to skip side effects
            // (analytics, rate limiting, etc.) while still enforcing security checks
            boolean isPrefetch = "1".equals(req.headers("X-Obsidian-Prefetch"));
            req.attribute("prefetch", isPrefetch);

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
        after((req, res) -> SessionMiddleware.clear());

        logger.info("Web server started on port {}", Obsidian.getWebPort());
    }
}