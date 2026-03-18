package com.obsidian.core.http.middleware;

import com.obsidian.core.http.middleware.builtin.DatabaseCloseMiddleware;
import com.obsidian.core.http.middleware.builtin.DatabaseMiddleware;
import com.obsidian.core.livecomponents.session.SessionMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Middleware execution manager.
 * Handles instantiation and execution of middleware classes for routes.
 * and executed automatically before and after every route, ahead of user-defined middleware.</p>
 */
public class MiddlewareManager
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(MiddlewareManager.class);

    /** Singleton instances of middleware classes */
    private static final Map<Class<? extends Middleware>, Middleware> instances = new HashMap<>();

    /** Built-in middlewares executed before every route */
    private static final List<Class<? extends Middleware>> globalBefore = new ArrayList<>();

    /** Built-in middlewares executed after every route */
    private static final List<Class<? extends Middleware>> globalAfter = new ArrayList<>();

    /**
     * Registers built-in framework middlewares.
     * Must be called once at application startup.
     */
    public static void registerBuiltins()
    {
        globalBefore.add(DatabaseMiddleware.class);
        globalBefore.add(SessionMiddleware.class);
        globalAfter.add(DatabaseCloseMiddleware.class);
    }

    /**
     * Executes before-route middleware chain.
     * Built-in middlewares run first, followed by route-specific middlewares.
     *
     * @param middlewareClasses Array of middleware classes to execute
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if any middleware throws an exception
     */
    public static void executeBefore(Class<? extends Middleware>[] middlewareClasses, Request req, Response res) throws Exception
    {
        for (Class<? extends Middleware> middlewareClass : globalBefore) {
            execute(middlewareClass, req, res);
        }
        for (Class<? extends Middleware> middlewareClass : middlewareClasses) {
            execute(middlewareClass, req, res);
        }
    }

    /**
     * Executes after-route middleware chain.
     * Built-in middlewares run first, followed by route-specific middlewares.
     *
     * @param middlewareClasses Array of middleware classes to execute
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if any middleware throws an exception
     */
    public static void executeAfter(Class<? extends Middleware>[] middlewareClasses, Request req, Response res) throws Exception
    {
        for (Class<? extends Middleware> middlewareClass : globalAfter) {
            execute(middlewareClass, req, res);
        }
        for (Class<? extends Middleware> middlewareClass : middlewareClasses) {
            execute(middlewareClass, req, res);
        }
    }

    /**
     * Executes a single middleware instance.
     *
     * @param middlewareClass Middleware class to execute
     * @param req HTTP request
     * @param res HTTP response
     * @throws Exception if the middleware throws an exception
     */
    private static void execute(Class<? extends Middleware> middlewareClass, Request req, Response res) throws Exception
    {
        Middleware middleware = getInstance(middlewareClass);
        logger.debug("Executing middleware: {}", middlewareClass.getSimpleName());
        middleware.handle(req, res);
    }

    /**
     * Gets or creates singleton instance of middleware.
     *
     * @param middlewareClass Middleware class
     * @return Middleware instance
     * @throws RuntimeException if instantiation fails
     */
    private static Middleware getInstance(Class<? extends Middleware> middlewareClass)
    {
        return instances.computeIfAbsent(middlewareClass, cls -> {
            try {
                return cls.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("Failed to instantiate middleware: {}", cls.getName(), e);
                throw new RuntimeException("Could not instantiate middleware: " + cls.getName(), e);
            }
        });
    }
}