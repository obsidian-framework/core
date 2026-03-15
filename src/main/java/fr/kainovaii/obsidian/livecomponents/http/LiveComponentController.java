package fr.kainovaii.obsidian.livecomponents.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.kainovaii.obsidian.security.csrf.annotations.CsrfProtect;
import fr.kainovaii.obsidian.livecomponents.core.ComponentManager;
import fr.kainovaii.obsidian.livecomponents.core.ComponentRequest;
import fr.kainovaii.obsidian.livecomponents.core.ComponentResponse;
import fr.kainovaii.obsidian.http.controller.annotations.Controller;
import fr.kainovaii.obsidian.http.middleware.annotations.Before;
import fr.kainovaii.obsidian.http.middleware.builtin.RateLimitMiddleware;
import fr.kainovaii.obsidian.routing.methods.GET;
import fr.kainovaii.obsidian.routing.methods.POST;
import spark.Request;
import spark.Response;

import java.util.Map;

/**
 * HTTP controller for LiveComponent action handling and lazy mounting.
 */
@Controller
public class LiveComponentController
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handles LiveComponent action requests.
     *
     * @param req HTTP request
     * @param res HTTP response
     * @param componentManager Component manager instance (injected)
     * @return JSON response with updated HTML and state
     */
    @CsrfProtect
    @Before(RateLimitMiddleware.class)
    @POST(value = "/obsidian/components", name = "obsidian.components.handle")
    public Object handleAction(Request req, Response res, ComponentManager componentManager)
    {
        try {
            ComponentRequest componentRequest = objectMapper.readValue(req.body(), ComponentRequest.class);
            ComponentResponse componentResponse = componentManager.handleAction(componentRequest, req.session(true), req, res);
            res.type("application/json");
            return objectMapper.writeValueAsString(componentResponse);
        } catch (Exception e) {
            ComponentResponse errorResponse = ComponentResponse.error("Server error: " + e.getMessage());
            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception jsonError) {
                return "{\"success\":false,\"error\":\"Fatal error\"}";
            }
        }
    }

    /**
     * Lazy mount endpoint — mounts a component on demand after page load.
     * Called by the JS runtime when a {@code [live:lazy]} placeholder is detected.
     *
     * @param req HTTP request
     * @param res HTTP response
     * @param componentManager Component manager instance (injected)
     * @return JSON {@code {success, html}} or {@code {success, error}}
     */
    @Before(RateLimitMiddleware.class)
    @GET(value = "/obsidian/components/mount", name = "obsidian.components.mount")
    public Object lazyMount(Request req, Response res, ComponentManager componentManager)
    {
        res.type("application/json");
        try {
            String componentName = req.queryParams("component");
            if (componentName == null || componentName.isEmpty()) {
                return objectMapper.writeValueAsString(
                        ComponentResponse.error("Missing 'component' query parameter"));
            }

            Map<String, Object> props = null;
            String propsJson = req.queryParams("props");
            if (propsJson != null && !propsJson.isEmpty()) {
                //noinspection unchecked
                props = objectMapper.readValue(propsJson, Map.class);
            }

            String html = componentManager.mount(componentName, req.session(true), req, props);
            return objectMapper.writeValueAsString(ComponentResponse.success(html, Map.of()));

        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(
                        ComponentResponse.error("Mount error: " + e.getMessage()));
            } catch (Exception jsonError) {
                return "{\"success\":false,\"error\":\"Fatal error\"}";
            }
        }
    }
}