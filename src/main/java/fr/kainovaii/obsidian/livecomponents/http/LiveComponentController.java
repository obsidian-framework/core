package fr.kainovaii.obsidian.livecomponents.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.kainovaii.obsidian.security.csrf.annotations.CsrfProtect;
import fr.kainovaii.obsidian.livecomponents.core.ComponentManager;
import fr.kainovaii.obsidian.livecomponents.core.ComponentRequest;
import fr.kainovaii.obsidian.livecomponents.core.ComponentResponse;
import fr.kainovaii.obsidian.http.controller.annotations.Controller;
import fr.kainovaii.obsidian.http.middleware.annotations.Before;
import fr.kainovaii.obsidian.http.middleware.builtin.RateLimitMiddleware;
import fr.kainovaii.obsidian.routing.methods.POST;
import spark.Request;
import spark.Response;

/**
 * HTTP controller for LiveComponent action handling.
 * Receives action requests from client, executes them, and returns updated component state.
 */
@Controller
public class LiveComponentController
{
    /** JSON serializer/deserializer */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handles LiveComponent action requests.
     * Deserializes request, executes action via ComponentManager, returns JSON response.
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
}