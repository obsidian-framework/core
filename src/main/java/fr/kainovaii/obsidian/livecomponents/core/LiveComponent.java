package fr.kainovaii.obsidian.livecomponents.core;

import fr.kainovaii.obsidian.livecomponents.annotations.State;
import fr.kainovaii.obsidian.livecomponents.ComponentException;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import spark.Request;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all LiveComponents — server-side reactive UI components.
 * Components maintain state, handle user interactions, and re-render automatically.
 * Subclasses must implement {@link #template()} to return the Pebble template path.
 */
public abstract class LiveComponent
{
    /** Unique component identifier, included in state */
    @State
    protected String _id;

    /** Current HTTP request — transient, not serialized in state */
    protected transient Request request;

    /** Snapshot of component state used for diffing and hydration */
    protected transient Map<String, Object> stateSnapshot = new HashMap<>();

    /**
     * Constructor.
     * Generates a unique component ID on instantiation.
     */
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
     * Sets the current HTTP request on this component.
     * Called by {@link ComponentManager} before each mount and action execution.
     *
     * @param request Current HTTP request
     */
    public void setRequest(Request request) {
        this.request = request;
    }

    /**
     * Returns the current HTTP request.
     *
     * @return Current HTTP request, or null if not set
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Returns the current HTTP session.
     *
     * @return Current Spark session, or null if request is not set or no session exists
     */
    protected spark.Session getSession() {
        if (request == null) return null;
        return request.session(false);
    }

    /**
     * Retrieves a session attribute by key.
     * Safe to call even if no session exists — returns null in that case.
     *
     * @param key Session attribute key
     * @return Attribute value, or null if not found
     */
    protected Object getSessionAttribute(String key)
    {
        if (request == null || request.session(false) == null) return null;
        return request.session(false).attribute(key);
    }

    /**
     * Captures the current state of all {@link State}-annotated fields.
     * Used for state synchronization between renders.
     */
    public void captureState()
    {
        stateSnapshot.clear();
        for (Field field : getAllFields(this.getClass()))
        {
            if (field.isAnnotationPresent(State.class)) {
                field.setAccessible(true);
                try {
                    stateSnapshot.put(field.getName(), field.get(this));
                } catch (IllegalAccessException e) {
                    throw new ComponentException("Cannot capture state field: " + field.getName(), e);
                }
            }
        }
    }

    /**
     * Hydrates component state from the provided map.
     * Used when restoring a component from the client's serialized state.
     *
     * @param state Map of field names to values
     */
    public void hydrate(Map<String, Object> state)
    {
        for (Field field : getAllFields(this.getClass())) {
            if (field.isAnnotationPresent(State.class)) {
                field.setAccessible(true);
                try {
                    Object value = state.get(field.getName());
                    if (value != null) {
                        field.set(this, convertValue(value, field.getType()));
                    }
                } catch (IllegalAccessException e) {
                    throw new ComponentException.StateHydrationException(field.getName(), e);
                }
            }
        }
    }

    /**
     * Renders the component to HTML using the Pebble template engine.
     * Exposes all {@link State}-annotated fields and public getter methods to the template context.
     *
     * @param pebble Pebble engine instance
     * @return Rendered HTML string
     * @throws ComponentException if template loading or rendering fails
     */
    public String render(PebbleEngine pebble)
    {
        try {
            PebbleTemplate template = pebble.getTemplate(template());
            Map<String, Object> context = new HashMap<>();
            context.put("_id", _id);

            // Expose @State fields
            for (Field field : getAllFields(this.getClass())) {
                if (field.isAnnotationPresent(State.class)) {
                    field.setAccessible(true);
                    context.put(field.getName(), field.get(this));
                }
            }

            // Expose public getters
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
     * Used for {@code live:model} bindings from the client.
     *
     * @param fieldName Name of the field to update
     * @param value     New value to set
     * @throws ComponentException if the field is not found or not annotated with {@link State}
     */
    public void updateField(String fieldName, Object value)
    {
        try {
            Field field = findField(this.getClass(), fieldName);
            if (field != null && field.isAnnotationPresent(State.class)) {
                field.setAccessible(true);
                field.set(this, convertValue(value, field.getType()));
            } else {
                throw new ComponentException("Field '" + fieldName + "' not found or not marked with @State");
            }
        } catch (IllegalAccessException e) {
            throw new ComponentException("Cannot update field: " + fieldName, e);
        }
    }

    /**
     * Collects all declared fields from the class hierarchy, including inherited fields.
     *
     * @param clazz Starting class
     * @return Array of all fields up to (but not including) {@link Object}
     */
    private Field[] getAllFields(Class<?> clazz)
    {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(java.util.Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * Converts a value to the specified target type.
     * Handles primitive wrappers and String conversion.
     *
     * @param value      Value to convert
     * @param targetType Target class
     * @return Converted value, or the original value if no conversion is needed
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
     * Searches for a field by name in the class hierarchy.
     *
     * @param clazz     Starting class
     * @param fieldName Field name to search for
     * @return Matching {@link Field}, or null if not found
     */
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

    /**
     * Returns the unique component identifier.
     *
     * @return Component ID
     */
    public String getId() { return _id; }

    /**
     * Returns the current state snapshot.
     *
     * @return Map of field names to their current values
     */
    public Map<String, Object> getStateSnapshot() { return stateSnapshot; }
}