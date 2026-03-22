package com.obsidian.core.livecomponents.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.obsidian.core.di.Container;
import com.obsidian.core.error.ErrorHandler;
import com.obsidian.core.livecomponents.ComponentException;
import com.obsidian.core.livecomponents.annotations.Action;
import com.obsidian.core.livecomponents.annotations.Prop;
import io.pebbletemplates.pebble.PebbleEngine;
import org.eclipse.jetty.websocket.api.Session;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages the full lifecycle of LiveComponents: registration, mounting, action handling,
 * and server-initiated push updates.
 */
public class ComponentManager
{
    private PebbleEngine pebbleEngine;

    private final Map<String, Class<? extends LiveComponent>> registeredComponents = new ConcurrentHashMap<>();

    private final Cache<String, LiveComponent> activeComponents = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    /** WebSocket sessions keyed by {@code sessionId:componentId}, used for server-push. */
    private final Map<String, Session> wsSessions = new ConcurrentHashMap<>();

    /**
     * Creates a new ComponentManager with the given Pebble engine.
     *
     * @param pebbleEngine the template engine used to render component HTML
     */
    public ComponentManager(PebbleEngine pebbleEngine) {
        this.pebbleEngine = pebbleEngine;
    }

    /**
     * Replaces the Pebble engine used for rendering.
     * Called by {@link LiveComponentsLoader} after extensions are registered.
     *
     * @param pebbleEngine the new template engine
     */
    public void setPebbleEngine(PebbleEngine pebbleEngine) {
        this.pebbleEngine = pebbleEngine;
    }

    /**
     * Registers a component class under the given name.
     * The name is used by templates to mount the component via {@code component('Name')}.
     *
     * @param name           component name used in templates
     * @param componentClass the class to instantiate when mounting
     */
    public void register(String name, Class<? extends LiveComponent> componentClass) {
        registeredComponents.put(name, componentClass);
    }

    /**
     * Returns whether a component with the given name has been registered.
     *
     * @param name component name to check
     * @return {@code true} if the name is registered, {@code false} otherwise
     */
    public boolean isRegistered(String name) {
        return registeredComponents.containsKey(name);
    }

    /**
     * Mounts a new component instance with no props.
     *
     * @param componentName name of the registered component
     * @param session        current HTTP session used as the cache key
     * @param req            current HTTP request bound to the component instance
     * @return the rendered HTML string for the initial render
     * @throws ComponentException.ComponentNotFoundException if the name is not registered
     */
    public String mount(String componentName, spark.Session session, Request req) {
        return mount(componentName, session, req, null);
    }

    /**
     * Mounts a new component instance, injecting the provided props before {@link LiveComponent#onMount()}.
     *
     * @param componentName name of the registered component
     * @param session        current HTTP session used as the cache key
     * @param req            current HTTP request bound to the component instance
     * @param props          map of {@link Prop} field names to values; may be {@code null}
     * @return the rendered HTML string for the initial render
     * @throws ComponentException.ComponentNotFoundException if the name is not registered
     * @throws ComponentException if instantiation, prop injection, or rendering fails
     */
    public String mount(String componentName, spark.Session session, Request req, Map<String, Object> props)
    {
        try {
            Class<? extends LiveComponent> cls = registeredComponents.get(componentName);
            if (cls == null) throw new ComponentException.ComponentNotFoundException(componentName);

            LiveComponent instance = cls.getDeclaredConstructor().newInstance();
            Container.injectFields(instance);
            instance.setRequest(req);

            if (props != null && !props.isEmpty()) injectProps(instance, props);

            instance.onMount();

            String sessionId = session != null ? session.id() : "anonymous";
            activeComponents.put(sessionId + ":" + instance.getId(), instance);
            instance.captureState();
            return instance.render(pebbleEngine);

        } catch (ComponentException e) {
            throw e;
        } catch (Exception e) {
            throw new ComponentException("Failed to mount component: " + componentName, e);
        }
    }

    /**
     * Handles an incoming component action from the client.
     *
     * @param request the deserialized action request from the client
     * @param session the current HTTP session
     * @param req     the current Spark HTTP request
     * @param res     the current Spark HTTP response
     * @return a {@link ComponentResponse} containing rendered HTML, redirect, event, or error
     */
    public ComponentResponse handleAction(ComponentRequest request, spark.Session session,
                                          Request req, Response res)
    {
        try {
            String sessionId = session != null ? session.id() : "anonymous";
            String key       = sessionId + ":" + request.getComponentId();

            LiveComponent component = activeComponents.getIfPresent(key);
            if (component == null) {
                return ComponentResponse.error("Component expired or not found. Please refresh the page.");
            }

            synchronized (component) {
                component.hydrate(request.getState());
                component.setRequest(req);
                Container.injectFields(component);

                ComponentResponse actionResult = executeAction(component, request.getAction(), request.getParams());

                component.runWatchers();
                component.onUpdate();

                Map<String, Object> diff = component.computeStateDiff();
                component.captureState();

                if (actionResult != null && actionResult.getRedirect() != null) {
                    return actionResult;
                }

                String html = component.render(pebbleEngine);
                ComponentResponse merged = ComponentResponse.success(html, component.getStateSnapshot(), diff);

                if (actionResult != null && actionResult.getEvent() != null) {
                    merged.setEvent(actionResult.getEvent());
                    merged.setEventPayload(actionResult.getEventPayload());
                }
                return merged;
            }

        } catch (Exception e) {
            String errorHtml = ErrorHandler.handle(e, req, res);
            return ComponentResponse.error(e.getMessage(), errorHtml);
        }
    }

    /**
     * Pushes a full re-render of a component to its connected WebSocket client.
     * Does nothing if the component is not found in the cache or has no active WebSocket connection.
     *
     * @param httpSessionId the HTTP session ID of the target user
     * @param componentId   the UUID of the component to push
     */
    public void push(String httpSessionId, String componentId) {
        String key = httpSessionId + ":" + componentId;
        LiveComponent component = activeComponents.getIfPresent(key);
        if (component == null) return;

        synchronized (component) {
            component.captureState();
            String html = component.render(pebbleEngine);
            sendWs(key, ComponentResponse.success(html, component.getStateSnapshot()));
        }
    }

    /**
     * Pushes a single field patch to the client without re-rendering the full component.
     * The JS runtime applies the new value to {@code [live:patch="fieldName"]} elements in-place.
     * Does nothing if the component is not found in the cache or has no active WebSocket connection.
     *
     * @param httpSessionId the HTTP session ID of the target user
     * @param componentId   the UUID of the component to patch
     * @param fieldName     the {@link com.obsidian.core.livecomponents.annotations.State} field name to update
     * @param value         the new field value
     */
    public void patch(String httpSessionId, String componentId, String fieldName, Object value) {
        String key = httpSessionId + ":" + componentId;
        LiveComponent component = activeComponents.getIfPresent(key);
        if (component == null) return;

        synchronized (component) {
            component.updateField(fieldName, value);
            sendWs(key, ComponentResponse.patch(Map.of(fieldName, value)));
        }
    }

    /**
     * Registers a WebSocket session for a mounted component, enabling server-push via
     * {@link #push} and {@link #patch}.
     *
     * @param httpSessionId the HTTP session ID of the connecting user
     * @param componentId   the UUID of the component being subscribed
     * @param wsSession     the Jetty WebSocket session to register
     */
    public void registerWsSession(String httpSessionId, String componentId, Session wsSession) {
        wsSessions.put(httpSessionId + ":" + componentId, wsSession);
    }

    /**
     * Removes the WebSocket session for a component, typically called on disconnect.
     *
     * @param httpSessionId the HTTP session ID of the disconnecting user
     * @param componentId   the UUID of the component being unsubscribed
     */
    public void unregisterWsSession(String httpSessionId, String componentId) {
        wsSessions.remove(httpSessionId + ":" + componentId);
    }

    /**
     * Serializes a {@link ComponentResponse} to JSON and sends it over the WebSocket connection
     * associated with {@code key}. Removes the session from the registry if the send fails.
     *
     * @param key      the {@code sessionId:componentId} registry key
     * @param response the response to serialize and send
     */
    private void sendWs(String key, ComponentResponse response) {
        Session ws = wsSessions.get(key);
        if (ws == null || !ws.isOpen()) return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            ws.getRemote().sendString(mapper.writeValueAsString(response));
        } catch (IOException e) {
            wsSessions.remove(key);
        }
    }

    /**
     * Injects values from {@code props} into the {@link Prop}-annotated fields of {@code component}.
     * Traverses the full class hierarchy. Values are coerced to the field's declared type
     * via {@link ValueConverter}.
     *
     * @param component the component instance to populate
     * @param props     map of field names to prop values
     * @throws ComponentException if a prop field cannot be set
     */
    private void injectProps(LiveComponent component, Map<String, Object> props) {
        Class<?> clazz = component.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(Prop.class)) {
                    Object value = props.get(f.getName());
                    if (value != null) {
                        try {
                            f.setAccessible(true);
                            f.set(component, ValueConverter.convert(value, f.getType()));
                        } catch (IllegalAccessException e) {
                            throw new ComponentException("Cannot inject prop: " + f.getName(), e);
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Resolves and executes an action on the component.
     *
     * @param component  the component instance on which to execute the action
     * @param actionName the action name or built-in identifier
     * @param params     positional parameters for the action method; may be {@code null} or empty
     * @return an explicit {@link ComponentResponse} if the action returned one, otherwise {@code null}
     * @throws ComponentException.ActionNotFoundException if no matching method is found
     * @throws ComponentException if the method is not annotated with {@link Action} or invocation fails
     */
    private ComponentResponse executeAction(LiveComponent component, String actionName, List<Object> params) {
        try {
            if ("__refresh".equals(actionName)) return null;

            if (actionName.startsWith("updateField_")) {
                String fieldName = actionName.substring("updateField_".length());
                Object value = (params != null && !params.isEmpty()) ? params.get(0) : null;
                component.updateField(fieldName, value);
                return null;
            }

            Method method = findMethod(component.getClass(), actionName, params != null ? params.size() : 0);
            if (method == null) {
                throw new ComponentException.ActionNotFoundException(
                        component.getClass().getSimpleName(), actionName);
            }

            if (!method.isAnnotationPresent(Action.class)) {
                throw new ComponentException("Method '" + actionName + "' on "
                        + component.getClass().getSimpleName()
                        + " is not annotated with @Action and cannot be called from the client.");
            }

            method.setAccessible(true);

            Object result;
            if (method.getParameterCount() == 0) {
                result = method.invoke(component);
            } else {
                Object[] converted = new Object[method.getParameterCount()];
                Class<?>[] types   = method.getParameterTypes();
                for (int i = 0; i < method.getParameterCount(); i++) {
                    converted[i] = (params != null && i < params.size())
                            ? ValueConverter.convert(params.get(i), types[i]) : null;
                }
                result = method.invoke(component, converted);
            }

            return result instanceof ComponentResponse ? (ComponentResponse) result : null;

        } catch (ComponentException e) {
            throw e;
        } catch (Exception e) {
            throw new ComponentException("Failed to execute action: " + actionName, e);
        }
    }

    /**
     * Searches for a declared method with the given name and parameter count,
     * traversing the class hierarchy from {@code clazz} upward.
     *
     * @param clazz      class to start searching from
     * @param methodName method name to look for
     * @param paramCount expected number of parameters
     * @return the first matching {@link Method}, or {@code null} if none is found
     */
    private Method findMethod(Class<?> clazz, String methodName, int paramCount) {
        while (clazz != null && clazz != Object.class) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) return m;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Removes all active component instances associated with the given HTTP session
     * from both the component cache and the WebSocket session registry.
     *
     * @param session the HTTP session whose components should be evicted; does nothing if {@code null}
     */
    public void clearSession(spark.Session session) {
        if (session == null) return;
        String prefix = session.id() + ":";
        activeComponents.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        wsSessions.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Returns the estimated number of active component instances across all sessions.
     *
     * @return estimated active component count
     */
    public int getActiveComponentCount() { return (int) activeComponents.estimatedSize(); }

    /**
     * Returns the Pebble engine currently used for rendering.
     *
     * @return the active {@link PebbleEngine}
     */
    public PebbleEngine getPebbleEngine() { return pebbleEngine; }
}