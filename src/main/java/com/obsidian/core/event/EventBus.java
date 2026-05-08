package com.obsidian.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The event dispatcher. Code dispatches events here; registered listeners
 * are invoked synchronously in priority order.
 * The bus is safe to use from multiple threads. Listener registration is
 * usually a one-shot boot operation, but additions are still safe at runtime.
 */
public final class EventBus {
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    /**
     * Indexed by exact event class. Subtype lookup walks the hierarchy.
     */
    private final ConcurrentHashMap<Class<?>, List<RegisteredListener>> listenersByType =
            new ConcurrentHashMap<>();

    /**
     * Cache of resolved listener chains per concrete event class. Cleared on registration.
     */
    private final ConcurrentHashMap<Class<?>, List<RegisteredListener>> resolvedChainCache =
            new ConcurrentHashMap<>();

    /** Per-thread dispatch depth, used by the recursion guard. */
    private final ThreadLocal<Integer> dispatchDepth = ThreadLocal.withInitial(() -> 0);

    private volatile int maxDispatchDepth = 8;

    // ---------------------------------------------------------------------
    // Registration (typically called by EventListenerLoader at boot)
    // ---------------------------------------------------------------------

    /**
     * Registers a listener method. Normally invoked by
     * {@link EventListenerLoader}, but exposed here for tests and dynamic
     * scenarios.
     */
    public void register(Class<?> eventType, Object listenerInstance, Method method, int priority) {
        Objects.requireNonNull(eventType,        "eventType must not be null");
        Objects.requireNonNull(listenerInstance, "listenerInstance must not be null");
        Objects.requireNonNull(method,           "method must not be null");

        method.setAccessible(true);
        RegisteredListener rl = new RegisteredListener(listenerInstance, method, priority);

        listenersByType.compute(eventType, (k, existing) -> {
            List<RegisteredListener> updated = (existing == null)
                    ? new ArrayList<>()
                    : new ArrayList<>(existing);
            updated.add(rl);
            // Sort by priority DESC, stable so equal-priority preserves registration order.
            updated.sort(Comparator.comparingInt((RegisteredListener r) -> r.priority).reversed());
            return Collections.unmodifiableList(updated);
        });

        // Any registration invalidates resolved chains, since a new listener
        // on a base class affects every subclass that was already cached.
        resolvedChainCache.clear();

        logger.debug("Registered listener {}#{} for {} (priority={})",
                listenerInstance.getClass().getSimpleName(),
                method.getName(),
                eventType.getName(),
                priority);
    }

    // ---------------------------------------------------------------------
    // Dispatch
    // ---------------------------------------------------------------------

    /**
     * Dispatches an event to all matching listeners.
     *
     * @param event the event instance; must not be null
     * @throws EventRecursionException if dispatch depth exceeds the configured maximum
     */
    public void dispatch(Object event) {
        Objects.requireNonNull(event, "event must not be null");

        int depth = dispatchDepth.get();
        if (depth >= maxDispatchDepth) {
            // Reset to avoid wedging the thread on subsequent unrelated dispatches
            dispatchDepth.set(0);
            throw new EventRecursionException(
                    "Event dispatch depth exceeded " + maxDispatchDepth +
                            " — likely an infinite listener loop. Event: " + event.getClass().getName());
        }
        dispatchDepth.set(depth + 1);

        try {
            List<RegisteredListener> chain = resolveChain(event.getClass());
            if (chain.isEmpty()) return;

            boolean stoppable = event instanceof StoppableEvent;
            StoppableEvent stoppableView = stoppable ? (StoppableEvent) event : null;

            for (int i = 0; i < chain.size(); i++) {
                RegisteredListener rl = chain.get(i);
                if (stoppable && stoppableView.isPropagationStopped()) {
                    logger.debug("Propagation stopped for {} — {} remaining listener(s) skipped",
                            event.getClass().getSimpleName(),
                            chain.size() - i);
                    break;
                }
                invokeSafely(rl, event);
            }
        } finally {
            int newDepth = dispatchDepth.get() - 1;
            if (newDepth <= 0) {
                dispatchDepth.remove(); // avoid retaining ThreadLocal references
            } else {
                dispatchDepth.set(newDepth);
            }
        }
    }

    private List<RegisteredListener> resolveChain(Class<?> eventClass) {
        List<RegisteredListener> cached = resolvedChainCache.get(eventClass);
        if (cached != null) return cached;

        // Walk the class hierarchy + interfaces to find all matching listeners.
        // We collect, then re-sort, because listeners from different levels of
        // the hierarchy must interleave by priority — not be grouped by class.
        List<RegisteredListener> merged = new ArrayList<>();
        Set<Class<?>> visited = new HashSet<>();
        collectListeners(eventClass, merged, visited);
        merged.sort(Comparator.comparingInt((RegisteredListener r) -> r.priority).reversed());

        List<RegisteredListener> immutable = Collections.unmodifiableList(merged);
        resolvedChainCache.put(eventClass, immutable);
        return immutable;
    }

    private void collectListeners(Class<?> type, List<RegisteredListener> out, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return;
        List<RegisteredListener> direct = listenersByType.get(type);
        if (direct != null) out.addAll(direct);
        collectListeners(type.getSuperclass(), out, visited);
        for (Class<?> iface : type.getInterfaces()) {
            collectListeners(iface, out, visited);
        }
    }

    private void invokeSafely(RegisteredListener rl, Object event) {
        try {
            rl.method.invoke(rl.instance, event);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            // EventRecursionException is a flow-control signal raised by
            // dispatch() when a listener has dispatched too deeply. It MUST
            // propagate out of the entire dispatch chain to the original
            // caller — otherwise we silently swallow the recursion guard
            // and the loop continues unbounded once the stack unwinds one
            // level.
            if (cause instanceof EventRecursionException) {
                throw (EventRecursionException) cause;
            }

            // Listener threw a normal exception — log the actual cause
            // (InvocationTargetException wrapper is noise) and continue
            // with the rest of the chain.
            logger.error("Listener {}#{} threw on {}: {}",
                    rl.instance.getClass().getSimpleName(),
                    rl.method.getName(),
                    event.getClass().getSimpleName(),
                    cause.getMessage(),
                    cause);
        } catch (IllegalAccessException e) {
            // Should be impossible because we setAccessible(true) at registration.
            logger.error("Cannot invoke listener {}#{} (illegal access)",
                    rl.instance.getClass().getSimpleName(), rl.method.getName(), e);
        } catch (EventRecursionException e) {
            // Defensive: if a listener directly threw it (without reflection
            // wrapping — shouldn't happen via Method.invoke but just in case),
            // propagate.
            throw e;
        } catch (Throwable t) {
            // Catch-all so a buggy listener can never break the dispatch loop.
            // We intentionally don't re-throw VirtualMachineError here: an OOM
            // inside one listener typically still leaves the JVM usable, and
            // re-throwing would prevent unrelated listeners from running on a
            // future dispatch from the same thread.
            logger.error("Unexpected error invoking listener {}#{} on {}",
                    rl.instance.getClass().getSimpleName(),
                    rl.method.getName(),
                    event.getClass().getSimpleName(), t);
        }
    }

    // ---------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------

    /**
     * Maximum nested dispatch depth before the recursion guard fires.
     * Default 8. Set higher only if you have a legitimate reason — usually
     * a high value masks a design problem.
     */
    public void setMaxDispatchDepth(int max) {
        if (max < 1) throw new IllegalArgumentException("maxDispatchDepth must be >= 1");
        this.maxDispatchDepth = max;
    }

    public int getMaxDispatchDepth() {
        return maxDispatchDepth;
    }

    /** Returns the number of listeners registered for the exact type (no subtype walk). */
    public int listenerCountFor(Class<?> eventType) {
        List<RegisteredListener> list = listenersByType.get(eventType);
        return list == null ? 0 : list.size();
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private static final class RegisteredListener {
        final Object instance;
        final Method method;
        final int    priority;

        RegisteredListener(Object instance, Method method, int priority) {
            this.instance = instance;
            this.method   = method;
            this.priority = priority;
        }
    }
}