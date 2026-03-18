package com.obsidian.core.realtime.sse;

import com.obsidian.core.routing.Route;
import com.obsidian.core.routing.methods.SSE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.lang.reflect.Method;
import java.util.List;

import static spark.Spark.get;

/**
 * Loader for Server-Sent Events (SSE) endpoints.
 * Discovers @SSE annotated methods and registers them as SSE routes.
 */
public class SseLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(SseLoader.class);

    /**
     * Registers all SSE routes from controllers.
     *
     * @param controllers List of controller instances
     */
    public static void registerSseRoutes(List<Object> controllers)
    {
        int sseCount = 0;

        for (Object controller : controllers) {
            sseCount += registerControllerSseRoutes(controller);
        }

        logger.info("Loaded {} SSE routes", sseCount);
    }

    /**
     * Registers SSE routes from a single controller.
     *
     * @param controller Controller instance
     * @return Number of routes registered
     */
    private static int registerControllerSseRoutes(Object controller)
    {
        int count = 0;

        for (Method method : controller.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(SSE.class)) {
                registerSseRoute(controller, method);
                count++;
            }
        }

        return count;
    }

    /**
     * Registers a single SSE route.
     *
     * @param controller Controller instance
     * @param method Method to invoke
     */
    private static void registerSseRoute(Object controller, Method method)
    {
        SSE annotation = method.getAnnotation(SSE.class);
        String path = annotation.value();
        String name = annotation.name();

        Route.registerNamedRoute(name, path);

        get(path, (req, res) -> {
            configureSseResponse(res);
            return invokeMethod(controller, method, req, res);
        });

        logger.debug("Registered SSE route: {} -> {}", name, path);
    }

    /**
     * Configures HTTP response headers for SSE.
     * Sets content type, cache control, and connection headers.
     *
     * @param res HTTP response
     */
    private static void configureSseResponse(Response res)
    {
        res.type("text/event-stream; charset=UTF-8");
        res.header("Cache-Control", "no-cache");
        res.header("Connection", "keep-alive");
        res.header("X-Accel-Buffering", "no");
    }

    /**
     * Invokes SSE handler method with request and response parameters.
     *
     * @param controller Controller instance
     * @param method Method to invoke
     * @param req HTTP request
     * @param res HTTP response
     * @return Method return value
     * @throws Exception if invocation fails
     */
    private static Object invokeMethod(Object controller, Method method, Request req, Response res)
            throws Exception
    {
        method.setAccessible(true);

        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == Request.class) {
                args[i] = req;
            } else if (paramTypes[i] == Response.class) {
                args[i] = res;
            }
        }

        return method.invoke(controller, args);
    }
}