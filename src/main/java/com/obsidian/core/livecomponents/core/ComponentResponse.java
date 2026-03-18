package com.obsidian.core.livecomponents.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Response from LiveComponent action execution.
 * Contains rendered HTML, updated state, redirect instruction, client-side event, or error.
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
     * Optional client-side redirect URL.
     * When set, the JS runtime navigates to this URL instead of re-rendering.
     */
    private String redirect;

    /**
     * Optional client-side event name to dispatch after re-render.
     * The JS runtime dispatches a CustomEvent on {@code document} with this name.
     */
    private String event;

    /** Optional payload for the dispatched client-side event. */
    private Object eventPayload;

    public static ComponentResponse success(String html, Map<String, Object> state) {
        ComponentResponse r = new ComponentResponse();
        r.html = html;
        r.state = state;
        return r;
    }

    public static ComponentResponse redirect(String url) {
        ComponentResponse r = new ComponentResponse();
        r.redirect = url;
        return r;
    }

    public static ComponentResponse withEvent(String html, Map<String, Object> state, String event) {
        ComponentResponse r = success(html, state);
        r.event = event;
        return r;
    }

    public static ComponentResponse withEvent(String html, Map<String, Object> state, String event, Object payload) {
        ComponentResponse r = withEvent(html, state, event);
        r.eventPayload = payload;
        return r;
    }

    public static ComponentResponse error(String message, String errorHtml) {
        ComponentResponse r = new ComponentResponse();
        r.success = false;
        r.error = message;
        r.html = errorHtml;
        return r;
    }

    public static ComponentResponse error(String message) {
        ComponentResponse r = new ComponentResponse();
        r.success = false;
        r.error = message;
        return r;
    }

    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }

    public Map<String, Object> getState() { return state; }
    public void setState(Map<String, Object> state) { this.state = state; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getRedirect() { return redirect; }
    public void setRedirect(String redirect) { this.redirect = redirect; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public Object getEventPayload() { return eventPayload; }
    public void setEventPayload(Object eventPayload) { this.eventPayload = eventPayload; }
}