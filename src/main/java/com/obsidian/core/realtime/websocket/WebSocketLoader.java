package com.obsidian.core.realtime.websocket;

import com.obsidian.core.core.Obsidian;
import com.obsidian.core.di.ReflectionsProvider;
import com.obsidian.core.routing.methods.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static spark.Spark.webSocket;

/**
 * Loader for WebSocket handlers.
 * Discovers @WebSocket annotated classes and registers them as WebSocket endpoints.
 */
public class WebSocketLoader
{
    /** Logger instance */
    private static final Logger logger = LoggerFactory.getLogger(WebSocketLoader.class);

    /**
     * Scans for and registers all WebSocket handlers.
     * Discovers classes annotated with @WebSocket in base package.
     */
    public static void registerWebSockets()
    {
        logger.info("Scanning for WebSocket handlers in package:" + Obsidian.getBasePackage());

        Set<Class<?>> webSocketClasses = ReflectionsProvider.getTypesAnnotatedWith(WebSocket.class);

        logger.info("Found {} WebSocket handler(s)", webSocketClasses.size());

        for (Class<?> wsClass : webSocketClasses) {
            registerWebSocket(wsClass);
        }

        logger.info("Loaded {} WebSocket handlers", webSocketClasses.size());
    }

    /**
     * Registers a single WebSocket handler.
     *
     * @param wsClass WebSocket handler class
     */
    private static void registerWebSocket(Class<?> wsClass)
    {
        WebSocket annotation = wsClass.getAnnotation(WebSocket.class);
        String path = annotation.value();

        webSocket(path, wsClass);

        logger.info("✓ Registered WebSocket: {} -> {} (class: {})", path, wsClass.getSimpleName(), wsClass.getName());
    }
}