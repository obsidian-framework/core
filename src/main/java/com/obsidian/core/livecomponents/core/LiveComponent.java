package com.obsidian.core.livecomponents.core;

import com.obsidian.core.livecomponents.annotations.Computed;
import com.obsidian.core.livecomponents.annotations.ServerOnly;
import com.obsidian.core.livecomponents.annotations.State;
import com.obsidian.core.livecomponents.annotations.Watch;
import com.obsidian.core.livecomponents.ComponentException;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import spark.Request;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all LiveComponents — server-side reactive UI components.
 *
 * <p>A LiveComponent encapsulates state, handles user interactions via {@link com.obsidian.core.livecomponents.annotations.Action}-annotated
 * methods, and re-renders its Pebble template automatically after each action.</p>
 *
 * <p>Reactive annotations:</p>
 * <ul>
 *   <li>{@link State} — field is serialized into client-side state and restored on every request.</li>
 *   <li>{@link ServerOnly} — field is included in renders but never hydrated from client input, preventing forgery.</li>
 *   <li>{@link Computed} — no-arg method called after each action; its return value is injected into the template context under the method's property name.</li>
 *   <li>{@link Watch} — method called automatically when a specific {@link State} field changes value.</li>
 * </ul>
 */
public abstract class LiveComponent
{
    private static final Map<Class<?>, Field[]>                   STATE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method[]>                  COMPUTED_CACHE     = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, List<Method>>> WATCH_CACHE        = new ConcurrentHashMap<>();

    /** Unique component identifier included in every state snapshot. */
    @State
    protected String _id;

    /** Current HTTP request — transient, never serialized into state. */
    protected transient Request request;

    /** Snapshot of {@link State} field values captured after the last action, used for diffing. */
    protected transient Map<String, Object> stateSnapshot = new HashMap<>();

    /**
     * Initializes the component with a random UUID as its identifier.
     */
    public LiveComponent() {
        this._id = UUID.randomUUID().toString();
    }

    /**
     * Returns the Pebble template path for this component.
     *
     * @return template path relative to the classpath root, e.g. {@code "components/counter.html"}
     */
    public abstract String template();

    /**
     * Called once after {@link com.obsidian.core.livecomponents.annotations.Prop} fields are injected and before the first render.
     * Override to load initial data or set up state that depends on props.
     */
    public void onMount() {}

    /**
     * Called after every action execution and after {@link #runWatchers()}, before the final re-render.
     * Override to run cross-cutting logic such as logging or audit trails.
     */
    public void onUpdate() {}

    /**
     * Sets the current HTTP request on this component instance.
     *
     * @param request the Spark request for the current action cycle
     */
    public void setRequest(Request request) { this.request = request; }

    /**
     * Returns the current HTTP request bound to this component.
     *
     * @return the current Spark request, or {@code null} if none is set
     */
    public Request getRequest() { return request; }

    /**
     * Returns the current Spark session, or {@code null} if no session exists.
     *
     * @return the active session, or {@code null}
     */
    protected spark.Session getSession() {
        return request == null ? null : request.session(false);
    }

    /**
     * Reads an attribute from the current session.
     *
     * @param key session attribute key
     * @param <T> expected type of the attribute
     * @return the attribute value cast to {@code T}, or {@code null} if absent or no session exists
     */
    @SuppressWarnings("unchecked")
    protected <T> T session(String key) {
        if (request == null) return null;
        spark.Session s = request.session(false);
        return s == null ? null : (T) s.attribute(key);
    }

    /**
     * Writes an attribute to the current session, creating the session if it does not exist.
     *
     * @param key   session attribute key
     * @param value value to store
     */
    protected void session(String key, Object value) {
        if (request != null) request.session(true).attribute(key, value);
    }

    /**
     * Returns a query or form parameter value from the current request.
     *
     * @param name parameter name
     * @return the parameter value, or {@code null} if absent
     */
    protected String param(String name) {
        return request != null ? request.queryParams(name) : null;
    }

    /**
     * Returns a query or form parameter value, falling back to a default when absent.
     *
     * @param name         parameter name
     * @param defaultValue value returned when the parameter is absent
     * @return the parameter value, or {@code defaultValue} if absent
     */
    protected String param(String name, String defaultValue) {
        String v = param(name);
        return v != null ? v : defaultValue;
    }

    /**
     * Returns a route parameter value (e.g. the {@code :id} segment).
     *
     * @param name route parameter name
     * @return the route parameter value, or {@code null} if absent
     */
    protected String routeParam(String name) {
        return request != null ? request.params(name) : null;
    }

    /**
     * Signals a client-side redirect after the current action.
     * The component will not re-render; the browser navigates to {@code url} instead.
     *
     * @param url target URL
     * @return a redirect {@link ComponentResponse} — return this from your action method
     */
    protected ComponentResponse redirect(String url) {
        return ComponentResponse.redirect(url);
    }

    /**
     * Signals a client-side {@code CustomEvent} dispatch on {@code document} after re-render.
     *
     * @param event event name
     * @return a {@link ComponentResponse} carrying the event — return this from your action method
     */
    protected ComponentResponse emit(String event) {
        return ComponentResponse.withEvent(null, stateSnapshot, event);
    }

    /**
     * Signals a client-side {@code CustomEvent} dispatch with a payload after re-render.
     *
     * @param event   event name
     * @param payload JSON-serializable payload attached to {@code event.detail}
     * @return a {@link ComponentResponse} carrying the event and payload — return this from your action method
     */
    protected ComponentResponse emit(String event, Object payload) {
        return ComponentResponse.withEvent(null, stateSnapshot, event, payload);
    }

    /**
     * Captures the current value of every {@link State}-annotated field into {@link #stateSnapshot}.
     * Called by {@link ComponentManager} after mount and after each action cycle.
     */
    public void captureState() {
        stateSnapshot.clear();
        for (Field f : getStateFields(this.getClass())) {
            try { stateSnapshot.put(f.getName(), f.get(this)); }
            catch (IllegalAccessException e) {
                throw new ComponentException("Cannot capture state field: " + f.getName(), e);
            }
        }
    }

    /**
     * Hydrates {@link State}-annotated fields from the client-supplied state map.
     * Fields marked {@link ServerOnly} are skipped — their values can never be forged by the client.
     *
     * @param state map of field names to values received from the client
     */
    public void hydrate(Map<String, Object> state) {
        for (Field f : getStateFields(this.getClass())) {
            if (f.isAnnotationPresent(ServerOnly.class)) continue;
            try {
                Object v = state.get(f.getName());
                if (v != null) f.set(this, ValueConverter.convert(v, f.getType()));
            } catch (IllegalAccessException e) {
                throw new ComponentException.StateHydrationException(f.getName(), e);
            }
        }
    }

    /**
     * Runs all {@link Watch}-annotated methods whose watched {@link State} field changed
     * since the last {@link #captureState()} call.
     *
     * <p>Watchers fire in declaration order within each class, with superclass watchers
     * running before subclass ones. They may freely mutate other {@link State} fields;
     * those mutations will be reflected in the final render without an extra round-trip.</p>
     *
     * <p>Called by {@link ComponentManager} after the action executes and before {@link #onUpdate()}.</p>
     */
    public void runWatchers() {
        Map<String, List<Method>> watchers = getWatchers(this.getClass());
        if (watchers.isEmpty()) return;

        for (Field f : getStateFields(this.getClass())) {
            List<Method> methods = watchers.get(f.getName());
            if (methods == null) continue;
            try {
                Object oldVal = stateSnapshot.get(f.getName());
                Object newVal = f.get(this);
                if (!Objects.equals(oldVal, newVal)) {
                    for (Method m : methods) {
                        m.setAccessible(true);
                        if (m.getParameterCount() == 1) {
                            m.invoke(this, ValueConverter.convert(newVal, m.getParameterTypes()[0]));
                        } else {
                            m.invoke(this);
                        }
                    }
                }
            } catch (Exception e) {
                throw new ComponentException(
                        "Watcher failed for field '" + f.getName() + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * Renders this component to an HTML string using the Pebble template engine.
     *
     * <p>The template context contains, in order of precedence:</p>
     * <ol>
     *   <li>All {@link State}-annotated fields, keyed by field name.</li>
     *   <li>All {@link Computed}-annotated methods, keyed by their property name
     *       (e.g. {@code getFullName()} → {@code fullName}).</li>
     *   <li>All conventional {@code get*} / {@code is*} getters not already in the context.</li>
     * </ol>
     *
     * @param pebble the Pebble engine instance used to evaluate the template
     * @return the rendered HTML string
     * @throws ComponentException.TemplateNotFoundException if the template file cannot be found
     * @throws ComponentException if the template evaluation fails
     */
    public String render(PebbleEngine pebble) {
        try {
            PebbleTemplate tpl = pebble.getTemplate(template());
            StringWriter writer = new StringWriter();
            tpl.evaluate(writer, buildContext());
            return writer.toString();
        } catch (io.pebbletemplates.pebble.error.LoaderException e) {
            throw new ComponentException.TemplateNotFoundException(template(), e);
        } catch (Exception e) {
            throw new ComponentException("Render failed for " + getClass().getSimpleName(), e);
        }
    }

    /**
     * Computes a partial diff of {@link State} fields that changed since the last {@link #captureState()} call.
     * {@link ServerOnly} fields are excluded — they are never sent to the client.
     *
     * @return a map containing only the field names and new values that differ from the last snapshot,
     *         or an empty map if nothing changed
     */
    public Map<String, Object> computeStateDiff() {
        Map<String, Object> diff = new LinkedHashMap<>();
        for (Field f : getStateFields(this.getClass())) {
            if (f.isAnnotationPresent(ServerOnly.class)) continue;
            try {
                Object oldVal = stateSnapshot.get(f.getName());
                Object newVal = f.get(this);
                if (!Objects.equals(oldVal, newVal)) diff.put(f.getName(), newVal);
            } catch (IllegalAccessException e) {
                throw new ComponentException("Cannot diff field: " + f.getName(), e);
            }
        }
        return diff;
    }

    /**
     * Builds the full Pebble template context for this component.
     *
     * @return a map of variable names to values ready for template evaluation
     */
    private Map<String, Object> buildContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("_id", _id);

        for (Field f : getStateFields(this.getClass())) {
            try { ctx.put(f.getName(), f.get(this)); }
            catch (IllegalAccessException e) {
                throw new ComponentException("Cannot read @State field: " + f.getName(), e);
            }
        }

        for (Method m : getComputedMethods(this.getClass())) {
            try { ctx.put(resolvePropertyName(m.getName()), m.invoke(this)); }
            catch (Exception e) {
                throw new ComponentException(
                        "@Computed method '" + m.getName() + "' failed: " + e.getMessage(), e);
            }
        }

        for (Method m : this.getClass().getMethods()) {
            if (m.isAnnotationPresent(Computed.class)) continue;
            String n = m.getName();
            if ((n.startsWith("get") || n.startsWith("is")) && m.getParameterCount() == 0 && !n.equals("getClass")) {
                String prop = n.startsWith("is")
                        ? Character.toLowerCase(n.charAt(2)) + n.substring(3)
                        : Character.toLowerCase(n.charAt(3)) + n.substring(4);
                try { ctx.putIfAbsent(prop, m.invoke(this)); } catch (Exception ignored) {}
            }
        }
        return ctx;
    }

    /**
     * Updates a single {@link State}-annotated field by name.
     * Called by {@link ComponentManager} when processing {@code updateField_*} built-in actions.
     *
     * @param fieldName name of the {@link State} field to update
     * @param value     new value; will be coerced to the field's declared type via {@link ValueConverter}
     * @throws ComponentException if the field does not exist, is not annotated with {@link State}, or cannot be set
     */
    public void updateField(String fieldName, Object value) {
        try {
            Field f = findField(this.getClass(), fieldName);
            if (f != null && f.isAnnotationPresent(State.class)) {
                f.setAccessible(true);
                f.set(this, ValueConverter.convert(value, f.getType()));
            } else {
                throw new ComponentException("Field '" + fieldName + "' not found or not @State");
            }
        } catch (IllegalAccessException e) {
            throw new ComponentException("Cannot update field: " + fieldName, e);
        }
    }

    /**
     * Returns all {@link State}-annotated fields declared in {@code clazz} and its superclasses,
     * using a per-class cache to avoid repeated reflection.
     *
     * @param clazz the component class to inspect
     * @return array of accessible {@link State} fields in declaration order, superclass fields first
     */
    static Field[] getStateFields(Class<?> clazz) {
        return STATE_FIELDS_CACHE.computeIfAbsent(clazz, c -> {
            List<Field> list = new ArrayList<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                for (Field f : cur.getDeclaredFields()) {
                    if (f.isAnnotationPresent(State.class)) { f.setAccessible(true); list.add(f); }
                }
                cur = cur.getSuperclass();
            }
            return list.toArray(new Field[0]);
        });
    }

    /**
     * Returns all {@link Computed}-annotated, no-arg methods declared in {@code clazz} and its superclasses,
     * using a per-class cache.
     *
     * @param clazz the component class to inspect
     * @return array of accessible {@link Computed} methods
     */
    private static Method[] getComputedMethods(Class<?> clazz) {
        return COMPUTED_CACHE.computeIfAbsent(clazz, c -> {
            List<Method> list = new ArrayList<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                for (Method m : cur.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(Computed.class) && m.getParameterCount() == 0) {
                        m.setAccessible(true); list.add(m);
                    }
                }
                cur = cur.getSuperclass();
            }
            return list.toArray(new Method[0]);
        });
    }

    /**
     * Returns a map of field name → watcher methods for all {@link Watch}-annotated methods
     * declared in {@code clazz} and its superclasses, using a per-class cache.
     *
     * @param clazz the component class to inspect
     * @return unmodifiable map of watched field names to their corresponding watcher methods
     */
    private static Map<String, List<Method>> getWatchers(Class<?> clazz) {
        return WATCH_CACHE.computeIfAbsent(clazz, c -> {
            Map<String, List<Method>> map = new HashMap<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                for (Method m : cur.getDeclaredMethods()) {
                    Watch w = m.getAnnotation(Watch.class);
                    if (w != null) {
                        m.setAccessible(true);
                        map.computeIfAbsent(w.value(), k -> new ArrayList<>()).add(m);
                    }
                }
                cur = cur.getSuperclass();
            }
            return Collections.unmodifiableMap(map);
        });
    }

    /**
     * Traverses the class hierarchy to find a declared field with the given name.
     *
     * @param clazz     class to start searching from
     * @param fieldName name of the field to find
     * @return the {@link Field}, or {@code null} if not found in the hierarchy
     */
    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try { return clazz.getDeclaredField(fieldName); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    /**
     * Derives a JavaBeans property name from a getter method name.
     * {@code getFoo} → {@code foo}, {@code isBar} → {@code bar}, anything else → unchanged.
     *
     * @param methodName the method name to convert
     * @return the corresponding property name
     */
    private static String resolvePropertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3)
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        if (methodName.startsWith("is") && methodName.length() > 2)
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        return methodName;
    }

    /**
     * Returns the unique identifier of this component instance.
     *
     * @return the component UUID
     */
    public String getId() { return _id; }

    /**
     * Returns the last captured state snapshot.
     * The map contains field names and their values as of the last {@link #captureState()} call.
     *
     * @return the state snapshot map
     */
    public Map<String, Object> getStateSnapshot() { return stateSnapshot; }
}