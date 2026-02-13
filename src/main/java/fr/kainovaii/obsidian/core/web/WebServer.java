package fr.kainovaii.obsidian.core.web;

import fr.kainovaii.obsidian.core.security.role.RoleChecker;
import fr.kainovaii.obsidian.core.web.controller.ControllerLoader;
import fr.kainovaii.obsidian.core.Obsidian;
import fr.kainovaii.obsidian.core.web.controller.GlobalAdvice;
import fr.kainovaii.obsidian.core.web.websocket.WebSocketLoader;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static fr.kainovaii.obsidian.core.web.controller.BaseController.*;
import static fr.kainovaii.obsidian.core.web.template.TemplateManager.setGlobal;
import static spark.Spark.*;

public class WebServer
{
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    public void start()
    {
        ipAddress("0.0.0.0");
        port(Obsidian.getWebPort());
        staticFiles.location("/");

        logger.info("Loading WebSocket handlers...");
        WebSocketLoader.registerWebSockets();

        logger.info("Initializing Spark...");
        init();

        exception(Exception.class, (e, req, res) -> {
            logger.error("Unhandled exception on {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.type("application/json");
            res.body("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        });

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
        logger.info("Web server started on port {}", Obsidian.getWebPort());
    }
}