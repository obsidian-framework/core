package fr.kainovaii.obsidian.livecomponents.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
 * Manages LiveComponent lifecycle, state and actions.
 * Handles component registration, mounting, hydration and action execution.
 */
public class ComponentManager
{
    /** Pebble template engine */
    private final PebbleEngine pebbleEngine;

    /** Registry of component classes by name */
    private final Map<String, Class<? extends LiveComponent>> registeredComponents = new ConcurrentHashMap<>();

    /** Cache of active component instances */
    private final Cache<String, LiveComponent> activeComponents = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    /**
     * Constructor.
     *
     * @param pebbleEngine Pebble template engine instance
     */
    public ComponentManager(PebbleEngine pebbleEngine) {
        this.pebbleEngine = pebbleEngine;
    }

    /**
     * Registers a component class.
     *
     * @param name Component name for template usage
     * @param componentClass Component class
     */
    public void register(String name, Class<? extends LiveComponent> componentClass) {
        registeredComponents.put(name, componentClass);
    }

    /**
     * Mounts a new component instance.
     * Creates instance, caches it, and returns initial render.
     *
     * @param componentName Component name
     * @param session HTTP session
     * @return Rendered HTML
     * @throws ComponentException if component not found or mounting fails
     */
    public String mount(String componentName, Session session) {
        try {
            Class<? extends LiveComponent> componentClass = registeredComponents.get(componentName);
            if (componentClass == null) {
                throw new ComponentException.ComponentNotFoundException(componentName);
            }

            LiveComponent instance = componentClass.getDeclaredConstructor().newInstance();

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
     * Handles component action from client.
     * Hydrates component state, executes action, captures new state, re-renders.
     * On error, delegates to {@link ErrorHandler} and injects the error page into the component slot.
     *
     * @param request Component action request
     * @param session HTTP session
     * @param req Spark HTTP request (for ErrorHandler)
     * @param res Spark HTTP response (for ErrorHandler)
     * @return Response with rendered HTML or error page
     */
    public ComponentResponse handleAction(ComponentRequest request, Session session, Request req, Response res) {
        try {
            String sessionId = session != null ? session.id() : "anonymous";
            String key = sessionId + ":" + request.getComponentId();

            LiveComponent component = activeComponents.getIfPresent(key);
            if (component == null) {
                return ComponentResponse.error("Component expired or not found. Please refresh the page.");
            }

            // Synchronized to avoid race conditions
            synchronized (component) {
                component.hydrate(request.getState());
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
     * Executes action method on component.
     * Handles special actions like __refresh and updateField_*.
     *
     * @param component Component instance
     * @param actionName Action method name
     * @param params Action parameters
     * @throws ComponentException if action execution fails
     */
    private void executeAction(LiveComponent component, String actionName, List<Object> params) {
        try {
            // Special handling for live:poll refresh
            if ("__refresh".equals(actionName)) {
                return;
            }

            // Special handling for live:model updates
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
                // Convert parameters to correct types
                Object[] convertedParams = new Object[method.getParameterCount()];
                Class<?>[] paramTypes = method.getParameterTypes();

                for (int i = 0; i < method.getParameterCount(); i++) {
                    if (params != null && i < params.size()) {
                        convertedParams[i] = convertValue(params.get(i), paramTypes[i]);
                    } else {
                        convertedParams[i] = null;
                    }
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
     * Finds method by name and parameter count in class hierarchy.
     *
     * @param clazz Class to search
     * @param methodName Method name
     * @param paramCount Parameter count
     * @return Method or null if not found
     */
    private Method findMethod(Class<?> clazz, String methodName, int paramCount) {
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
     * Converts value to target type.
     *
     * @param value Value to convert
     * @param targetType Target class
     * @return Converted value
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) return value;

        if (targetType == Integer.class || targetType == int.class) {
            return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }
        if (targetType == String.class) {
            return value.toString();
        }
        return value;
    }

    /**
     * Gets count of active components in cache.
     *
     * @return Active component count
     */
    public int getActiveComponentCount() {
        return (int) activeComponents.estimatedSize();
    }

    /**
     * Gets Pebble engine instance.
     *
     * @return Pebble engine
     */
    public PebbleEngine getPebbleEngine() {
        return pebbleEngine;
    }

    /**
     * Clears all components for a session.
     * Called on logout or session invalidation.
     *
     * @param session HTTP session
     */
    public void clearSession(Session session) {
        if (session == null) return;
        String prefix = session.id() + ":";
        activeComponents.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}