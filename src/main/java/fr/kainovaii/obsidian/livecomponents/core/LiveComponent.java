package fr.kainovaii.obsidian.livecomponents.core;

import fr.kainovaii.obsidian.livecomponents.annotations.State;
import fr.kainovaii.obsidian.livecomponents.ComponentException;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import spark.Request;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all LiveComponents — server-side reactive UI components.
 * Components maintain state, handle user interactions, and re-render automatically.
 */
public abstract class LiveComponent
{
    /** Per-class cache of @State-annotated fields. */
    private static final Map<Class<?>, Field[]> STATE_FIELDS_CACHE = new ConcurrentHashMap<>();

    /** Unique component identifier, included in state. */
    @State
    protected String _id;

    /** Current HTTP request — transient, not serialized in state. */
    protected transient Request request;

    /** Snapshot of component state used for diffing and hydration. */
    protected transient Map<String, Object> stateSnapshot = new HashMap<>();

    public LiveComponent() {
        this._id = UUID.randomUUID().toString();
    }

    /**
     * Returns the Pebble template path for this component.
     *
     * @return Template path relative to the classpath root
     */
    public abstract String template();

    /**
     * Called once after props are injected and before the first render.
     * Override to initialize state that depends on props or session data.
     */
    public void onMount() {}

    /**
     * Called after every action execution, before re-render.
     * Override to run cross-cutting logic (logging, audit, etc.).
     */
    public void onUpdate() {}

    public void setRequest(Request request) { this.request = request; }
    public Request getRequest() { return request; }

    /** Returns the current Spark session, or null if none exists. */
    protected spark.Session getSession() {
        if (request == null) return null;
        return request.session(false);
    }

    /**
     * Returns a session attribute cast to {@code T}, or null if absent.
     *
     * @param key Session attribute key
     */
    @SuppressWarnings("unchecked")
    protected <T> T session(String key) {
        if (request == null) return null;
        spark.Session s = request.session(false);
        if (s == null) return null;
        return (T) s.attribute(key);
    }

    /**
     * Sets a session attribute.
     *
     * @param key   Session attribute key
     * @param value Value to store
     */
    protected void session(String key, Object value) {
        if (request == null) return;
        spark.Session s = request.session(true);
        s.attribute(key, value);
    }

    /**
     * Returns a query parameter value, or null if absent.
     *
     * @param name Query parameter name
     */
    protected String param(String name) {
        return request != null ? request.queryParams(name) : null;
    }

    /**
     * Returns a query parameter value, or {@code defaultValue} if absent.
     *
     * @param name         Query parameter name
     * @param defaultValue Fallback value
     */
    protected String param(String name, String defaultValue) {
        String val = param(name);
        return val != null ? val : defaultValue;
    }

    /**
     * Returns a route parameter value (e.g. {@code :id}), or null if absent.
     *
     * @param name Route parameter name
     */
    protected String routeParam(String name) {
        return request != null ? request.params(name) : null;
    }

    // -------------------------------------------------------------------------
    // Action response helpers
    // -------------------------------------------------------------------------

    /**
     * Signals a client-side redirect after the current action.
     * The component will not re-render; the browser navigates to {@code url}.
     *
     * @param url Target URL
     * @return Redirect response — return this from your action method
     */
    protected ComponentResponse redirect(String url) {
        return ComponentResponse.redirect(url);
    }

    /**
     * Signals a client-side CustomEvent dispatch after re-render.
     *
     * @param event Event name
     * @return Response with event — return this from your action method
     */
    protected ComponentResponse emit(String event) {
        return ComponentResponse.withEvent(null, stateSnapshot, event);
    }

    /**
     * Signals a client-side CustomEvent dispatch with a payload after re-render.
     *
     * @param event   Event name
     * @param payload JSON-serializable payload
     * @return Response with event and payload
     */
    protected ComponentResponse emit(String event, Object payload) {
        return ComponentResponse.withEvent(null, stateSnapshot, event, payload);
    }

    /** Captures the current state of all {@link State}-annotated fields. */
    public void captureState() {
        stateSnapshot.clear();
        for (Field field : getStateFields(this.getClass())) {
            try {
                stateSnapshot.put(field.getName(), field.get(this));
            } catch (IllegalAccessException e) {
                throw new ComponentException("Cannot capture state field: " + field.getName(), e);
            }
        }
    }

    /**
     * Hydrates component state from the provided map.
     *
     * @param state Map of field names to values
     */
    public void hydrate(Map<String, Object> state) {
        for (Field field : getStateFields(this.getClass())) {
            try {
                Object value = state.get(field.getName());
                if (value != null) {
                    field.set(this, ValueConverter.convert(value, field.getType()));
                }
            } catch (IllegalAccessException e) {
                throw new ComponentException.StateHydrationException(field.getName(), e);
            }
        }
    }

    /**
     * Renders the component to HTML using the Pebble template engine.
     *
     * @param pebble Pebble engine instance
     * @return Rendered HTML string
     */
    public String render(PebbleEngine pebble) {
        try {
            PebbleTemplate template = pebble.getTemplate(template());
            Map<String, Object> context = new HashMap<>();
            context.put("_id", _id);

            for (Field field : getStateFields(this.getClass())) {
                context.put(field.getName(), field.get(this));
            }

            for (java.lang.reflect.Method method : this.getClass().getMethods()) {
                String name = method.getName();
                if ((name.startsWith("get") || name.startsWith("is"))
                        && method.getParameterCount() == 0
                        && !name.equals("getClass")) {
                    String propertyName = name.startsWith("is")
                            ? Character.toLowerCase(name.charAt(2)) + name.substring(3)
                            : Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    context.put(propertyName, method.invoke(this));
                }
            }

            StringWriter writer = new StringWriter();
            template.evaluate(writer, context);
            return writer.toString();

        } catch (io.pebbletemplates.pebble.error.LoaderException e) {
            throw new ComponentException.TemplateNotFoundException(template(), e);
        } catch (Exception e) {
            throw new ComponentException("Failed to render component: " + this.getClass().getSimpleName(), e);
        }
    }

    /**
     * Updates a single {@link State}-annotated field by name.
     *
     * @param fieldName Name of the field to update
     * @param value     New value to set
     */
    public void updateField(String fieldName, Object value) {
        try {
            Field field = findField(this.getClass(), fieldName);
            if (field != null && field.isAnnotationPresent(State.class)) {
                field.setAccessible(true);
                field.set(this, ValueConverter.convert(value, field.getType()));
            } else {
                throw new ComponentException("Field '" + fieldName + "' not found or not marked with @State");
            }
        } catch (IllegalAccessException e) {
            throw new ComponentException("Cannot update field: " + fieldName, e);
        }
    }

    private static Field[] getStateFields(Class<?> clazz) {
        return STATE_FIELDS_CACHE.computeIfAbsent(clazz, c -> {
            List<Field> fields = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isAnnotationPresent(State.class)) {
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
                current = current.getSuperclass();
            }
            return fields.toArray(new Field[0]);
        });
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    public String getId() { return _id; }
    public Map<String, Object> getStateSnapshot() { return stateSnapshot; }
}