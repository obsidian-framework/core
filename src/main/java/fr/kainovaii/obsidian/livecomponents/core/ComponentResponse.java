package fr.kainovaii.obsidian.livecomponents.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Response from LiveComponent action execution.
 * Contains rendered HTML, updated state, or error information.
 */
public class ComponentResponse
{
    /** Rendered HTML after action execution */
    private String html;

    /** Updated component state */
    private Map<String, Object> state = new HashMap<>();

    /** Success flag */
    private boolean success = true;

    /** Error message if action failed */
    private String error;

    /**
     * Creates successful response with HTML and state.
     *
     * @param html Rendered HTML
     * @param state Updated state
     * @return Success response
     */
    public static ComponentResponse success(String html, Map<String, Object> state)
    {
        ComponentResponse response = new ComponentResponse();
        response.html = html;
        response.state = state;
        return response;
    }

    /**
     * Creates error response with rendered HTML from ErrorHandler.
     * The HTML is injected directly into the component slot on the client.
     *
     * @param message Error message
     * @param errorHtml Rendered HTML from ErrorHandler
     * @return Error response with visual feedback
     */
    public static ComponentResponse error(String message, String errorHtml)
    {
        ComponentResponse response = new ComponentResponse();
        response.success = false;
        response.error = message;
        response.html = errorHtml;
        return response;
    }

    /**
     * Creates error response without HTML (fallback, no visual rendering).
     *
     * @param message Error message
     * @return Error response
     */
    public static ComponentResponse error(String message)
    {
        ComponentResponse response = new ComponentResponse();
        response.success = false;
        response.error = message;
        return response;
    }

    /**
     * Gets rendered HTML.
     *
     * @return HTML string
     */
    public String getHtml() { return html; }

    /**
     * Sets rendered HTML.
     *
     * @param html HTML string
     */
    public void setHtml(String html) { this.html = html; }

    /**
     * Gets updated state.
     *
     * @return State map
     */
    public Map<String, Object> getState() { return state; }

    /**
     * Sets updated state.
     *
     * @param state State map
     */
    public void setState(Map<String, Object> state) { this.state = state; }

    /**
     * Checks if action was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() { return success; }

    /**
     * Sets success flag.
     *
     * @param success Success flag
     */
    public void setSuccess(boolean success) { this.success = success; }

    /**
     * Gets error message.
     *
     * @return Error message or null
     */
    public String getError() { return error; }

    /**
     * Sets error message.
     *
     * @param error Error message
     */
    public void setError(String error) { this.error = error; }
}