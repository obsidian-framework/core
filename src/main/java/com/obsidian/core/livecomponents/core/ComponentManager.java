package com.obsidian.core.livecomponents.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.obsidian.core.di.Container;
import com.obsidian.core.error.ErrorHandler;
import com.obsidian.core.livecomponents.ComponentException;
import com.obsidian.core.livecomponents.annotations.Action;
import com.obsidian.core.livecomponents.annotations.Prop;
import io.pebbletemplates.pebble.PebbleEngine;
import spark.Request;
import spark.Response;
import spark.Session;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages the full lifecycle of LiveComponents — registration, mounting, hydration, and action execution.
 * Component instances are cached per session and expire after one hour of inactivity.
 */
public class ComponentManager
{
    /** Pebble template engine used for rendering components. */
    private PebbleEngine pebbleEngine;

    /** Registry of component classes indexed by component name. */
    private final Map<String, Class<? extends LiveComponent>> registeredComponents = new ConcurrentHashMap<>();

    /** Cache of active component instances keyed by {@code sessionId:componentId}. */
    private final Cache<String, LiveComponent> activeComponents = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    public ComponentManager(PebbleEngine pebbleEngine) {
        this.pebbleEngine = pebbleEngine;
    }

    public void setPebbleEngine(PebbleEngine pebbleEngine) {
        this.pebbleEngine = pebbleEngine;
    }

    public void register(String name, Class<? extends LiveComponent> componentClass) {
        registeredComponents.put(name, componentClass);
    }

    public boolean isRegistered(String name) {
        return registeredComponents.containsKey(name);
    }

    /**
     * Mounts a new component instance with no props.
     *
     * @param componentName Component name as registered
     * @param session        Current HTTP session
     * @param req            Current HTTP request
     * @return Rendered HTML string
     */
    public String mount(String componentName, Session session, Request req) {
        return mount(componentName, session, req, null);
    }

    /**
     * Mounts a new component instance, injecting the provided props before {@code onMount()}.
     *
     * @param componentName Component name as registered
     * @param session        Current HTTP session
     * @param req            Current HTTP request
     * @param props          Map of prop field names to values — injected into {@code @Prop} fields
     * @return Rendered HTML string
     */
    public String mount(String componentName, Session session, Request req, Map<String, Object> props)
    {
        try {
            Class<? extends LiveComponent> componentClass = registeredComponents.get(componentName);
            if (componentClass == null) {
                throw new ComponentException.ComponentNotFoundException(componentName);
            }

            LiveComponent instance = componentClass.getDeclaredConstructor().newInstance();
            Container.injectFields(instance);
            instance.setRequest(req);

            if (props != null && !props.isEmpty()) {
                injectProps(instance, props);
            }

            instance.onMount();

            String sessionId = session != null ? session.id() : "anonymous";
            String key = sessionId + ":" + instance.getId();

            activeComponents.put(key, instance);
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
     * Hydrates state, validates the action against {@code @Action}, executes it,
     * calls {@code onUpdate()}, captures new state, and re-renders.
     *
     * @param request Component action request
     * @param session Current HTTP session
     * @param req     Current Spark HTTP request
     * @param res     Current Spark HTTP response
     * @return {@link ComponentResponse} with rendered HTML, redirect, event, or error
     */
    public ComponentResponse handleAction(ComponentRequest request, Session session, Request req, Response res)
    {
        try {
            String sessionId = session != null ? session.id() : "anonymous";
            String key = sessionId + ":" + request.getComponentId();

            LiveComponent component = activeComponents.getIfPresent(key);
            if (component == null) {
                return ComponentResponse.error("Component expired or not found. Please refresh the page.");
            }

            synchronized (component) {
                component.hydrate(request.getState());
                component.setRequest(req);
                Container.injectFields(component);

                ComponentResponse actionResult = executeAction(component, request.getAction(), request.getParams());
                component.onUpdate();
                component.captureState();

                // Action returned an explicit response (redirect / emit)
                if (actionResult != null) {
                    if (actionResult.getRedirect() != null) {
                        return actionResult;
                    }
                    // Merge event metadata into a normal success response
                    String html = component.render(pebbleEngine);
                    ComponentResponse merged = ComponentResponse.success(html, component.getStateSnapshot());
                    if (actionResult.getEvent() != null) {
                        merged.setEvent(actionResult.getEvent());
                        merged.setEventPayload(actionResult.getEventPayload());
                    }
                    return merged;
                }

                String html = component.render(pebbleEngine);
                return ComponentResponse.success(html, component.getStateSnapshot());
            }

        } catch (Exception e) {
            String errorHtml = ErrorHandler.handle(e, req, res);
            return ComponentResponse.error(e.getMessage(), errorHtml);
        }
    }

    /**
     * Injects props into {@code @Prop}-annotated fields of the component.
     *
     * @param component Component instance
     * @param props     Props map
     */
    private void injectProps(LiveComponent component, Map<String, Object> props)
    {
        Class<?> clazz = component.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Prop.class)) {
                    Object value = props.get(field.getName());
                    if (value != null) {
                        try {
                            field.setAccessible(true);
                            field.set(component, ValueConverter.convert(value, field.getType()));
                        } catch (IllegalAccessException e) {
                            throw new ComponentException("Cannot inject prop: " + field.getName(), e);
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Executes an action method on the component.
     * Built-in actions ({@code __refresh}, {@code updateField_*}) bypass the {@code @Action} guard.
     * All other actions must be annotated with {@link Action}.
     *
     * @param component  Component instance
     * @param actionName Action name or built-in identifier
     * @param params     Parameters
     * @return Explicit {@link ComponentResponse} if the action returned one, otherwise null
     */
    private ComponentResponse executeAction(LiveComponent component, String actionName, List<Object> params)
    {
        try {
            if ("__refresh".equals(actionName)) {
                return null;
            }

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
                Object[] convertedParams = new Object[method.getParameterCount()];
                Class<?>[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < method.getParameterCount(); i++) {
                    convertedParams[i] = (params != null && i < params.size())
                            ? ValueConverter.convert(params.get(i), paramTypes[i])
                            : null;
                }
                result = method.invoke(component, convertedParams);
            }

            return result instanceof ComponentResponse ? (ComponentResponse) result : null;

        } catch (ComponentException e) {
            throw e;
        } catch (Exception e) {
            throw new ComponentException("Failed to execute action: " + actionName, e);
        }
    }

    private Method findMethod(Class<?> clazz, String methodName, int paramCount)
    {
        while (clazz != null && clazz != Object.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == paramCount) {
                    return method;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    public void clearSession(Session session)
    {
        if (session == null) return;
        String prefix = session.id() + ":";
        activeComponents.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    public int getActiveComponentCount() { return (int) activeComponents.estimatedSize(); }
    public PebbleEngine getPebbleEngine() { return pebbleEngine; }
}