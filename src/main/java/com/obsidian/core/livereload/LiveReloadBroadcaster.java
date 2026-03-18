package com.obsidian.core.livereload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts reload events to connected browser clients via SSE.
 * Maintains at most one active SSE connection per client IP address,
 * replacing any previous connection when a new one is registered.
 */
public class LiveReloadBroadcaster
{
    private static final Logger logger = LoggerFactory.getLogger(LiveReloadBroadcaster.class);
    private final Map<String, HttpServletResponse> clients = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE client, replacing any existing connection from the same IP.
     * This ensures only one active SSE connection per client at any time.
     *
     * @param ip       The client's IP address, used as a unique key
     * @param response The HTTP response to write SSE events to
     */
    public void addClient(String ip, HttpServletResponse response) {
        clients.put(ip, response);
        logger.debug("[LiveReload] Client registered for IP {}. Total: {}", ip, clients.size());
    }

    /**
     * Removes a disconnected client from the active SSE connections pool.
     * Called when a client disconnects or the SSE connection is interrupted.
     *
     * @param ip The client's IP address
     */
    public void removeClient(String ip)
    {
        clients.remove(ip);
        logger.debug("[LiveReload] Client removed for IP {}. Total: {}", ip, clients.size());
    }

    /**
     * Broadcasts a reload event to all connected clients.
     * Removes disconnected clients automatically.
     */
    public void broadcast()
    {
        if (clients.isEmpty()) return;

        logger.info("[LiveReload] Broadcasting reload to {} client(s)...", clients.size());

        clients.entrySet().removeIf(entry -> {
            try {
                PrintWriter writer = entry.getValue().getWriter();
                writer.write("data: reload\n\n");
                writer.flush();
                return writer.checkError();
            } catch (IOException e) {
                return true;
            }
        });
    }

    /**
     * Returns the number of connected clients.
     */
    public int getClientCount()
    {
        return clients.size();
    }
}