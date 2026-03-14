package fr.kainovaii.obsidian.livecomponents.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves Obsidian Flow Drive JavaScript from JAR resources.
 */
public class ObsidianFlowScriptRoute implements Route
{
    private static final Logger logger = LoggerFactory.getLogger(ObsidianFlowScriptRoute.class);
    private static String cachedContent;

    static {
        try {
            InputStream is = ObsidianFlowScriptRoute.class.getClassLoader().getResourceAsStream("META-INF/resources/obsidian/flow.js");

            if (is != null) {
                cachedContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                logger.info("Obsidian Flow script loaded successfully");
            } else {
                logger.error("Obsidian Flow script not found in JAR");
                cachedContent = "console.error('Obsidian Flow script not found');";
            }
        } catch (Exception e) {
            logger.error("Failed to load Obsidian Flow script", e);
            cachedContent = "console.error('Failed to load Obsidian Flow');";
        }
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        response.type("application/javascript; charset=utf-8");
        response.header("Cache-Control", "public, max-age=31536000");
        return cachedContent;
    }
}