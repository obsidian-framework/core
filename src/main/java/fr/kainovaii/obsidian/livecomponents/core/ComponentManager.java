package fr.kainovaii.obsidian.livecomponents.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.kainovaii.obsidian.di.Container;
import fr.kainovaii.obsidian.error.ErrorHandler;
import fr.kainovaii.obsidian.livecomponents.ComponentException;
import io.pebbletemplates.pebble.PebbleEngine;
import spark.Request;
import spark.Response;
import spark.Session;

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
    /** Pebble template engine used for rendering components */
    private final PebbleEngine pebbleEngine;

    /** Registry of component classes indexed by component name */
    private final Map<String, Class<? extends LiveComponent>> registeredComponents = new ConcurrentHashMap<>();

    /** Cache of active component instances keyed by sessionId:componentId */
    private final Cache<String, LiveComponent> activeComponents = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    /**
     * Constructs a new ComponentManager.
     *
     * @param pebbleEngine Pebble template engine instance
     */
    public ComponentManager(PebbleEngine pebbleEngine) {
        this.pebbleEngine = pebbleEngine;
    }

    /**
     * Registers a component class under the given name.
     * The name is used in templates via {@code component('Name')}.
     *
     * @param name           Component name
     * @param componentClass Component class to register
     */
    public void register(String name, Class<? extends LiveComponent> componentClass) {
        registeredComponents.put(name, componentClass);
    }

    /**
     * Mounts a new component instance for the given session and request.
     * Creates the instance, injects the request, caches it, and returns the initial render.
     *
     * @param componentName Component name as registered
     * @param session        Current HTTP session
     * @param req            Current HTTP request
     * @return Rendered HTML string
     * @throws ComponentException if the component is not found or mounting fails
     */
    public String mount(String componentName, Session session, Request req)
    {
        try {
            Class<? extends LiveComponent> componentClass = registeredComponents.get(componentName);
            if (componentClass == null) {
                throw new ComponentException.ComponentNotFoundException(componentName);
            }

            LiveComponent instance = componentClass.getDeclaredConstructor().newInstance();
            Container.injectFields(instance);
            instance.setRequest(req);

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
     * Hydrates the component state, injects the current request, executes the action,
     * captures the new state, and re-renders the component.
     * On error, delegates to {@link ErrorHandler} and wraps the error page in the response.
     *
     * @param request Component action request containing state, action name and params
     * @param session Current HTTP session
     * @param req     Current Spark HTTP request
     * @param res     Current Spark HTTP response
     * @return {@link ComponentResponse} with rendered HTML or error details
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
                executeAction(component, request.getAction(), request.getParams());
                component.captureState();

                String html = component.render(pebbleEngine);
                return ComponentResponse.success(html, component.getStateSnapshot());
            }

        } catch (Exception e) {
            String errorHtml = ErrorHandler.handle(e, req, res);
            return ComponentResponse.error(e.getMessage(), errorHtml);
        }
    }

    /**
     * Executes an action method on the given component instance.
     * Handles special built-in actions such as {@code __refresh} and {@code updateField_*}.
     *
     * @param component  Component instance to invoke the action on
     * @param actionName Action method name or special action identifier
     * @param params     List of parameters to pass to the action method
     * @throws ComponentException if the action is not found or execution fails
     */
    private void executeAction(LiveComponent component, String actionName, List<Object> params)
    {
        try {
            if ("__refresh".equals(actionName)) {
                return;
            }

            if (actionName.startsWith("updateField_")) {
                String fieldName = actionName.substring("updateField_".length());
                Object value = (params != null && !params.isEmpty()) ? params.get(0) : null;
                component.updateField(fieldName, value);
                return;
            }

            Method method = findMethod(component.getClass(), actionName, params != null ? params.size() : 0);
            if (method == null) {
                throw new ComponentException.ActionNotFoundException(
                        component.getClass().getSimpleName(),
                        actionName
                );
            }

            method.setAccessible(true);

            if (method.getParameterCount() == 0) {
                method.invoke(component);
            } else {
                Object[] convertedParams = new Object[method.getParameterCount()];
                Class<?>[] paramTypes = method.getParameterTypes();

                for (int i = 0; i < method.getParameterCount(); i++) {
                    convertedParams[i] = (params != null && i < params.size())
                            ? convertValue(params.get(i), paramTypes[i])
                            : null;
                }

                method.invoke(component, convertedParams);
            }

        } catch (ComponentException e) {
            throw e;
        } catch (Exception e) {
            throw new ComponentException("Failed to execute action: " + actionName, e);
        }
    }

    /**
     * Searches for a method by name and parameter count in the class hierarchy.
     *
     * @param clazz       Class to search
     * @param methodName  Method name
     * @param paramCount  Expected parameter count
     * @return Matching {@link Method}, or null if not found
     */
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

    /**
     * Converts a value to the specified target type.
     * Handles primitive wrappers and String conversion.
     *
     * @param value      Value to convert
     * @param targetType Target class
     * @return Converted value, or the original if no conversion is needed
     */
    private Object convertValue(Object value, Class<?> targetType)
    {
        if (value == null || targetType.isInstance(value)) return value;

        if (targetType == Integer.class || targetType == int.class)
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        if (targetType == Long.class || targetType == long.class)
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        if (targetType == Double.class || targetType == double.class)
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        if (targetType == Boolean.class || targetType == boolean.class)
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        if (targetType == String.class)
            return value.toString();

        return value;
    }

    /**
     * Returns the number of active components currently held in cache.
     *
     * @return Estimated active component count
     */
    public int getActiveComponentCount() {
        return (int) activeComponents.estimatedSize();
    }

    /**
     * Returns the Pebble engine instance used for rendering.
     *
     * @return Pebble engine
     */
    public PebbleEngine getPebbleEngine() {
        return pebbleEngine;
    }

    /**
     * Removes all cached components associated with the given session.
     * Should be called on logout or session invalidation.
     *
     * @param session HTTP session to clear
     */
    public void clearSession(Session session)
    {
        if (session == null) return;
        String prefix = session.id() + ":";
        activeComponents.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}