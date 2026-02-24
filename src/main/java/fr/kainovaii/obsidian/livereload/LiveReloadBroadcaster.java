package fr.kainovaii.obsidian.livereload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts reload events to connected browser clients via SSE.
 */
public class LiveReloadBroadcaster
{
    private static final Logger logger = LoggerFactory.getLogger(LiveReloadBroadcaster.class);
    private final Set<HttpServletResponse> clients = ConcurrentHashMap.newKeySet();

    /**
     * Registers a new SSE client.
     *
     * @param response The HTTP response to write SSE events to
     */
    public void addClient(HttpServletResponse response)
    {
        clients.add(response);
        logger.debug("[LiveReload] New client connected. Total: {}", clients.size());
    }

    /**
     * Broadcasts a reload event to all connected clients.
     * Removes disconnected clients automatically.
     */
    public void broadcast()
    {
        if (clients.isEmpty()) return;

        logger.info("[LiveReload] Broadcasting reload to {} client(s)...", clients.size());

        Iterator<HttpServletResponse> it = clients.iterator();
        while (it.hasNext()) {
            HttpServletResponse client = it.next();
            try {
                PrintWriter writer = client.getWriter();
                writer.write("data: reload\n\n");
                writer.flush();
                if (writer.checkError()) {
                    it.remove();
                }
            } catch (IOException e) {
                it.remove();
            }
        }
    }

    /**
     * Returns the number of connected clients.
     */
    public int getClientCount()
    {
        return clients.size();
    }
}