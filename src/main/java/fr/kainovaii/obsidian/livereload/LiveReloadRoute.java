package fr.kainovaii.obsidian.livereload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.PrintWriter;

/**
 * Spark route that handles Server-Sent Events (SSE) connections for live reload.
 * Keeps the connection open and sends periodic pings to prevent timeout.
 * Broadcasts a "reload" event to the browser whenever a file change is detected.
 */
public class LiveReloadRoute implements Route
{
    private static final Logger logger = LoggerFactory.getLogger(LiveReloadRoute.class);

    /** Interval between keep-alive pings in milliseconds. */
    private static final long PING_INTERVAL_MS = 25_000;

    private final LiveReloadBroadcaster broadcaster;

    /**
     * Creates a new LiveReloadRoute with the given broadcaster.
     *
     * @param broadcaster The broadcaster used to register SSE clients
     */
    public LiveReloadRoute(LiveReloadBroadcaster broadcaster)
    {
        this.broadcaster = broadcaster;
    }

    /**
     * Handles an incoming SSE connection request.
     * Configures the response headers for SSE, registers the client in the broadcaster,
     * then blocks the thread with periodic pings until the client disconnects.
     *
     * @param request  The incoming HTTP request
     * @param response The HTTP response
     * @return An empty string to prevent Spark from overwriting the raw response
     * @throws Exception If an unexpected error occurs during handling
     */
    @Override
    public Object handle(Request request, Response response) throws Exception
    {
        response.raw().setContentType("text/event-stream");
        response.raw().setCharacterEncoding("UTF-8");
        response.raw().setHeader("Cache-Control", "no-cache");
        response.raw().setHeader("X-Accel-Buffering", "no");
        response.raw().setHeader("Connection", "keep-alive");

        PrintWriter writer = response.raw().getWriter();

        writer.write(": connected\n\n");
        writer.flush();

        String ip = request.ip();
        broadcaster.addClient(ip, response.raw());
        logger.debug("[LiveReload] SSE client connected for IP {}.", ip);

        try {
            while (!writer.checkError()) {
                Thread.sleep(PING_INTERVAL_MS);
                writer.write(": ping\n\n");
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("[LiveReload] SSE connection interrupted.");
        } finally {
            broadcaster.removeClient(ip);
            logger.debug("[LiveReload] SSE client disconnected for IP {}.", ip);
        }

        return "";
    }
}