package fr.kainovaii.core.web;

import fr.kainovaii.core.security.role.RoleChecker;
import fr.kainovaii.core.web.controller.ControllerLoader;
import fr.kainovaii.core.Spark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static spark.Spark.*;

public class WebServer
{
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    public void start()
    {
        ipAddress("0.0.0.0");
        port(Spark.getWebPort());
        staticFiles.location("/");

        exception(Exception.class, (e, req, res) -> {
            logger.error("Unhandled exception on {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.type("application/json");
            res.body("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        });

        before((req, res) -> {
            try {
                Class<?> globalAdvice = Class.forName("fr.kainovaii.spark.app.controllers.GlobalAdviceController");
                Method applyGlobals = globalAdvice.getMethod("applyGlobals", spark.Request.class);
                applyGlobals.invoke(null, req);
            } catch (ClassNotFoundException e) {} catch (Exception e) {
                logger.warn("Error calling GlobalAdviceController.applyGlobals", e);
            }
            RoleChecker.checkAccess(req, res);
        });

        ControllerLoader.loadControllers();
        logger.info("Web server started on port {}", Spark.getWebPort());
    }
}