package com.obsidian.core.livecomponents.http;

import com.obsidian.core.livecomponents.core.ComponentManager;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Jetty WebSocket handler that registers and unregisters client connections
 * for server-initiated LiveComponent push updates.
 */
@WebSocket
public class LiveComponentWsHandler
{
    private static final Logger logger = LoggerFactory.getLogger(LiveComponentWsHandler.class);

    private final ComponentManager componentManager;

    /** HTTP session ID extracted from the WebSocket handshake query parameters. */
    private String httpSessionId;

    /** Component UUID extracted from the WebSocket handshake query parameters. */
    private String componentId;

    /**
     * Creates a new handler bound to the given component manager.
     *
     * @param componentManager the manager used to register and unregister WebSocket sessions
     */
    public LiveComponentWsHandler(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    /**
     * Called when a WebSocket connection is established.
     * Extracts {@code sessionId} and {@code componentId} from the handshake query parameters
     * and registers the session with the {@link ComponentManager}.
     *
     * @param session the newly opened Jetty WebSocket session
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        httpSessionId = session.getUpgradeRequest().getParameterMap()
                .getOrDefault("sessionId", List.of("anonymous")).get(0);
        componentId   = session.getUpgradeRequest().getParameterMap()
                .getOrDefault("componentId", List.of("")).get(0);

        if (componentId.isEmpty()) {
            logger.warn("[LiveComponents WS] Missing componentId — closing connection");
            session.close(1008, "Missing componentId");
            return;
        }

        componentManager.registerWsSession(httpSessionId, componentId, session);
        logger.debug("[LiveComponents WS] Connected: {}:{}", httpSessionId, componentId);
    }

    /**
     * Called when the WebSocket connection is closed, either by the client or the server.
     * Unregisters the session from the {@link ComponentManager} so subsequent push calls
     * do not attempt to write to a closed socket.
     *
     * @param session    the closed Jetty WebSocket session
     * @param statusCode the WebSocket close status code
     * @param reason     a human-readable reason for the closure, may be empty
     */
    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        componentManager.unregisterWsSession(httpSessionId, componentId);
        logger.debug("[LiveComponents WS] Closed: {}:{} (status={})", httpSessionId, componentId, statusCode);
    }

    /**
     * Called when a WebSocket error occurs.
     * Logs the error and unregisters the session to prevent future write attempts.
     *
     * @param session the affected Jetty WebSocket session
     * @param cause   the exception that triggered the error
     */
    @OnWebSocketError
    public void onError(Session session, Throwable cause) {
        logger.error("[LiveComponents WS] Error for {}:{}: {}", httpSessionId, componentId, cause.getMessage());
        componentManager.unregisterWsSession(httpSessionId, componentId);
    }
}