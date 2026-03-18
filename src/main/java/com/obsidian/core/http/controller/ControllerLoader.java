package com.obsidian.core.http.controller;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.di.Container;
import com.obsidian.core.http.controller.annotations.Controller;
import com.obsidian.core.http.controller.annotations.GlobalAdvice;
import com.obsidian.core.http.middleware.MiddlewareManager;
import com.obsidian.core.security.role.RoleChecker;
import com.obsidian.core.routing.RouteLoader;
import com.obsidian.core.realtime.sse.SseLoader;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static spark.Spark.before;

/**
* Controller discovery and registration system.
* Scans for @Controller and @GlobalAdvice annotated classes and registers their routes.
*/
public class ControllerLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(ControllerLoader.class);

    /**
     * Loads and registers all controllers in application.
     * Discovers @Controller classes, registers routes and SSE endpoints.
     */
    public static void loadControllers()
    {
        MiddlewareManager.registerBuiltins();
        before("/*", RoleChecker::checkAccess);
        List<Object> controllers = discoverControllers();
        RouteLoader.registerRoutes(controllers);
        SseLoader.registerSseRoutes(controllers);

        logger.info("Loaded {} controllers", controllers.size());
    }

    /**
     * Discovers all @Controller annotated classes.
     *
     * @return List of instantiated controller objects
     */
    private static List<Object> discoverControllers()
    {
        Reflections reflections = new Reflections(Obsidian.getBasePackage());
        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(Controller.class);

        return controllerClasses.stream()
                .map(ControllerLoader::instantiateController)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Loads and executes @GlobalAdvice annotated classes.
     * Calls applyGlobals() method on each advice class.
     *
     * @param req HTTP request
     * @param res HTTP response
     */
    public static void loadAdvicesControllers(Request req, Response res)
    {
        try {
            MiddlewareManager.executeBefore(new Class[0], req, res);
            Reflections reflections = new Reflections(Obsidian.getBasePackage());
            Set<Class<?>> adviceClasses = reflections.getTypesAnnotatedWith(GlobalAdvice.class);

            for (Class<?> adviceClass : adviceClasses) {
                try {
                    Object adviceInstance = adviceClass.getDeclaredConstructor().newInstance();
                    Container.injectFields(adviceInstance);
                    Method applyGlobals = adviceClass.getMethod("applyGlobals", Request.class, Response.class);
                    applyGlobals.invoke(adviceInstance, req, res);
                } catch (NoSuchMethodException e) {
                    logger.info("@GlobalAdvice class {} doesn't have applyGlobals(Request, Response) method", adviceClass.getName());
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof spark.HaltException) {
                        throw (spark.HaltException) cause;
                    }
                    logger.error("Error calling applyGlobals on {}: {}", adviceClass.getName(), cause != null ? cause.getMessage() : "unknown", cause);
                } catch (Exception e) {
                    logger.error("Error calling applyGlobals on {}: {}", adviceClass.getName(), e.getMessage(), e);
                }
            }
        } catch (spark.HaltException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error scanning for @GlobalAdvice: {}", e.getMessage(), e);
        }
    }

    /**
     * Instantiates a controller class.
     *
     * @param cls Controller class
     * @return Controller instance or null if instantiation fails
     */
    private static Object instantiateController(Class<?> cls)
    {
        try {
            Object instance = cls.getDeclaredConstructor().newInstance();
            Container.injectFields(instance);
            return instance;
        } catch (Exception e) {
            logger.error("Failed to instantiate controller: {}", cls.getName(), e);
            return null;
        }
    }
}