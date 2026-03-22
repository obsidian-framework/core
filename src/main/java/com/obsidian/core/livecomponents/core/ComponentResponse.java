package com.obsidian.core.livecomponents.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the result of a LiveComponent action execution.
 * causing the JS runtime to dispatch a {@code CustomEvent} on {@code document} after re-render.</p>
 */
public class ComponentResponse
{
    /**
     * Discriminator that tells the JS runtime how to apply this response.
     */
    public enum Type { SUCCESS, PATCH, REDIRECT, ERROR }

    private Type               type         = Type.SUCCESS;
    private String             html;
    private Map<String, Object> state       = new HashMap<>();
    private Map<String, Object> diff;
    private boolean            success      = true;
    private String             error;
    private String             redirect;
    private String             event;
    private Object             eventPayload;

    /**
     * Creates a successful response with a full re-render and a complete state snapshot.
     *
     * @param html  the rendered HTML string
     * @param state the full state snapshot after the action
     * @return a {@link Type#SUCCESS} response
     */
    public static ComponentResponse success(String html, Map<String, Object> state) {
        ComponentResponse r = new ComponentResponse();
        r.type  = Type.SUCCESS;
        r.html  = html;
        r.state = state;
        return r;
    }

    /**
     * Creates a successful response with a full re-render, a complete state snapshot,
     * and a partial diff of only the fields that changed.
     *
     * @param html  the rendered HTML string
     * @param state the full state snapshot after the action
     * @param diff  map of field names to new values for fields that changed
     * @return a {@link Type#SUCCESS} response carrying the diff
     */
    public static ComponentResponse success(String html, Map<String, Object> state, Map<String, Object> diff) {
        ComponentResponse r = success(html, state);
        r.diff = diff;
        return r;
    }

    /**
     * Creates a lightweight patch response that updates specific fields in-place
     * without triggering a full component re-render.
     *
     * @param diff map of field names to their new values
     * @return a {@link Type#PATCH} response
     */
    public static ComponentResponse patch(Map<String, Object> diff) {
        ComponentResponse r = new ComponentResponse();
        r.type    = Type.PATCH;
        r.success = true;
        r.diff    = diff;
        return r;
    }

    /**
     * Creates a redirect response that signals the JS runtime to navigate to {@code url}.
     * The component is not re-rendered when a redirect is returned.
     *
     * @param url the target URL
     * @return a {@link Type#REDIRECT} response
     */
    public static ComponentResponse redirect(String url) {
        ComponentResponse r = new ComponentResponse();
        r.type     = Type.REDIRECT;
        r.redirect = url;
        return r;
    }

    /**
     * Creates a successful response that also dispatches a client-side {@code CustomEvent}
     * after re-render.
     *
     * @param html  the rendered HTML string
     * @param state the full state snapshot after the action
     * @param event the name of the {@code CustomEvent} to dispatch on {@code document}
     * @return a {@link Type#SUCCESS} response carrying the event
     */
    public static ComponentResponse withEvent(String html, Map<String, Object> state, String event) {
        ComponentResponse r = success(html, state);
        r.event = event;
        return r;
    }

    /**
     * Creates a successful response that dispatches a client-side {@code CustomEvent}
     * with a payload after re-render.
     *
     * @param html    the rendered HTML string
     * @param state   the full state snapshot after the action
     * @param event   the name of the {@code CustomEvent} to dispatch on {@code document}
     * @param payload a JSON-serializable object attached to {@code event.detail}
     * @return a {@link Type#SUCCESS} response carrying the event and payload
     */
    public static ComponentResponse withEvent(String html, Map<String, Object> state, String event, Object payload) {
        ComponentResponse r = withEvent(html, state, event);
        r.eventPayload = payload;
        return r;
    }

    /**
     * Creates an error response with a rendered error page.
     *
     * @param message  human-readable error description
     * @param errorHtml HTML rendered by the framework's error handler
     * @return a {@link Type#ERROR} response
     */
    public static ComponentResponse error(String message, String errorHtml) {
        ComponentResponse r = new ComponentResponse();
        r.type    = Type.ERROR;
        r.success = false;
        r.error   = message;
        r.html    = errorHtml;
        return r;
    }

    /**
     * Creates an error response without a rendered page, used when rendering itself is not possible.
     *
     * @param message human-readable error description
     * @return a {@link Type#ERROR} response
     */
    public static ComponentResponse error(String message) {
        ComponentResponse r = new ComponentResponse();
        r.type    = Type.ERROR;
        r.success = false;
        r.error   = message;
        return r;
    }

    /**
     * Returns the response type discriminator.
     *
     * @return the {@link Type} of this response
     */
    public Type getType() { return type; }

    /**
     * Returns the rendered HTML string, or {@code null} for patch and redirect responses.
     *
     * @return the HTML string, or {@code null}
     */
    public String getHtml() { return html; }

    /**
     * Sets the rendered HTML string.
     *
     * @param html the HTML string to set
     */
    public void setHtml(String html) { this.html = html; }

    /**
     * Returns the full state snapshot after the action.
     *
     * @return the state map
     */
    public Map<String, Object> getState() { return state; }

    /**
     * Sets the full state snapshot.
     *
     * @param state the state map to set
     */
    public void setState(Map<String, Object> state) { this.state = state; }

    /**
     * Returns the partial diff map containing only the fields that changed,
     * or {@code null} if no diff was computed.
     *
     * @return the diff map, or {@code null}
     */
    public Map<String, Object> getDiff() { return diff; }

    /**
     * Sets the partial diff map.
     *
     * @param diff the diff map to set
     */
    public void setDiff(Map<String, Object> diff) { this.diff = diff; }

    /**
     * Returns whether the action succeeded.
     *
     * @return {@code true} if successful, {@code false} on error
     */
    public boolean isSuccess() { return success; }

    /**
     * Sets the success flag.
     *
     * @param success {@code true} if successful
     */
    public void setSuccess(boolean success) { this.success = success; }

    /**
     * Returns the error message, or {@code null} if the action succeeded.
     *
     * @return the error message, or {@code null}
     */
    public String getError() { return error; }

    /**
     * Sets the error message.
     *
     * @param error the error message to set
     */
    public void setError(String error) { this.error = error; }

    /**
     * Returns the redirect target URL, or {@code null} if this is not a redirect response.
     *
     * @return the redirect URL, or {@code null}
     */
    public String getRedirect() { return redirect; }

    /**
     * Sets the redirect target URL.
     *
     * @param redirect the URL to redirect to
     */
    public void setRedirect(String redirect) { this.redirect = redirect; }

    /**
     * Returns the name of the client-side {@code CustomEvent} to dispatch after re-render,
     * or {@code null} if no event should be dispatched.
     *
     * @return the event name, or {@code null}
     */
    public String getEvent() { return event; }

    /**
     * Sets the client-side event name.
     *
     * @param event the event name to dispatch
     */
    public void setEvent(String event) { this.event = event; }

    /**
     * Returns the JSON-serializable payload attached to the client-side event,
     * or {@code null} if no payload was set.
     *
     * @return the event payload, or {@code null}
     */
    public Object getEventPayload() { return eventPayload; }

    /**
     * Sets the client-side event payload.
     *
     * @param eventPayload a JSON-serializable object to attach to {@code event.detail}
     */
    public void setEventPayload(Object eventPayload) { this.eventPayload = eventPayload; }
}