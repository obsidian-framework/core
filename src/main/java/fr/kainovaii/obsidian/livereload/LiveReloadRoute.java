package fr.kainovaii.obsidian.livereload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.PrintWriter;

/**
 * Spark route that handles SSE connections for live reload.
 * Keeps the connection alive and sends a ping every 25 seconds.
 *
 * Registered at: GET /__obsidian/livereload
 */
public class LiveReloadRoute implements Route
{
    private static final Logger logger = LoggerFactory.getLogger(LiveReloadRoute.class);
    private static final long PING_INTERVAL_MS = 25_000;

    private final LiveReloadBroadcaster broadcaster;

    public LiveReloadRoute(LiveReloadBroadcaster broadcaster)
    {
        this.broadcaster = broadcaster;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception
    {
        response.raw().setContentType("text/event-stream");
        response.raw().setCharacterEncoding("UTF-8");
        response.raw().setHeader("Cache-Control", "no-cache");
        response.raw().setHeader("X-Accel-Buffering", "no");
        response.raw().setHeader("Connection", "keep-alive");

        broadcaster.addClient(response.raw());

        // Keep connection alive with periodic pings
        PrintWriter writer = response.raw().getWriter();
        try {
            while (!request.raw().getAsyncContext().getResponse().isCommitted()) {
                Thread.sleep(PING_INTERVAL_MS);
                writer.write(": ping\n\n");
                writer.flush();
                if (writer.checkError()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Client disconnected — normal
            logger.debug("[LiveReload] Client disconnected.");
        }

        return null;
    }
}
