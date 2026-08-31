package com.obsidian.core.event;

import com.obsidian.core.event.annotations.Listener;
import com.obsidian.core.event.annotations.On;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest
{
    private EventBus bus;

    @BeforeEach
    void setUp() { bus = new EventBus(); }

    static class HelloEvent implements Event {
        final String message;
        HelloEvent(String message) { this.message = message; }
    }

    static class OtherEvent implements Event {}

    @Listener
    static class RecordingListener
    {
        final List<HelloEvent> received = new ArrayList<>();

        @On(HelloEvent.class)
        public void handle(HelloEvent event)
        {
            received.add(event);
        }
    }

    @Test
    void dispatch_invokesRegisteredListener()
    {
        RecordingListener listener = new RecordingListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        HelloEvent event = new HelloEvent("hello");
        bus.dispatch(event);

        assertEquals(1, listener.received.size());
        assertSame(event, listener.received.get(0));
    }

    @Test
    void dispatch_withNoListeners_doesNothing()
    {
        assertDoesNotThrow(() -> bus.dispatch(new HelloEvent("orphan")));
    }

    @Test
    void dispatch_nullEvent_throwsNpe()
    {
        assertThrows(NullPointerException.class, () -> bus.dispatch(null));
    }

    @Test
    void dispatch_unrelatedEvent_doesNotInvokeListener()
    {
        RecordingListener listener = new RecordingListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        bus.dispatch(new OtherEvent());

        assertTrue(listener.received.isEmpty());
    }

    @Listener
    static class PriorityListener
    {
        final List<String> calls = new ArrayList<>();

        @On(value = HelloEvent.class, priority = 0)
        public void low(HelloEvent event) { calls.add("low"); }

        @On(value = HelloEvent.class, priority = 10)
        public void high(HelloEvent event) { calls.add("high"); }

        @On(value = HelloEvent.class, priority = 5)
        public void mid(HelloEvent event) { calls.add("mid"); }
    }

    @Test
    void dispatch_invokesHandlersInDescendingPriorityOrder()
    {
        PriorityListener listener = new PriorityListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        bus.dispatch(new HelloEvent("ordered"));

        assertEquals(List.of("high", "mid", "low"), listener.calls);
    }

    @Listener
    static class StableOrderListener
    {
        final List<String> calls = new ArrayList<>();

        @On(HelloEvent.class)
        public void first(HelloEvent event) { calls.add("first"); }

        @On(HelloEvent.class)
        public void second(HelloEvent event) { calls.add("second"); }

        @On(HelloEvent.class)
        public void third(HelloEvent event) { calls.add("third"); }
    }

    @Test
    void dispatch_withEqualPriority_preservesRegistrationOrder()
    {
        StableOrderListener listener = new StableOrderListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        bus.dispatch(new HelloEvent("stable"));

        assertEquals(3, listener.calls.size());

        List<String> firstRun = new ArrayList<>(listener.calls);

        listener.calls.clear();
        bus.dispatch(new HelloEvent("stable2"));

        assertEquals(firstRun, listener.calls);
    }

    @Listener
    static class ThrowingListener
    {
        final List<String> calls = new ArrayList<>();

        @On(value = HelloEvent.class, priority = 10)
        public void boom(HelloEvent event)
        {
            calls.add("boom");
            throw new RuntimeException("intentional");
        }

        @On(value = HelloEvent.class, priority = 0)
        public void survivor(HelloEvent event)
        {
            calls.add("survivor");
        }
    }

    @Test
    void dispatch_whenHandlerThrows_otherHandlersStillRun()
    {
        ThrowingListener listener = new ThrowingListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        assertDoesNotThrow(() -> bus.dispatch(new HelloEvent("resilient")));

        assertEquals(List.of("boom", "survivor"), listener.calls);
    }

    // ===== UNSUBSCRIBE TESTS =====

    static class CancellableHelloEvent implements Cancellable {
        final String message;
        private boolean cancelled = false;

        CancellableHelloEvent(String message) { this.message = message; }

        @Override
        public void cancel() { this.cancelled = true; }

        @Override
        public boolean isCancelled() { return cancelled; }
    }

    @Listener
    static class UnsubscribableListener
    {
        final List<String> calls = new ArrayList<>();

        @On(CancellableHelloEvent.class)
        public void handle(CancellableHelloEvent event)
        {
            calls.add("handled");
        }
    }

    @Test
    void unregister_removesHandlerFromBus()
    {
        UnsubscribableListener listener = new UnsubscribableListener();
        long handlerId = bus.register(CancellableHelloEvent.class, listener,
                getMethod(listener, "handle"), 0);

        bus.dispatch(new CancellableHelloEvent("first"));
        assertEquals(1, listener.calls.size());

        bus.unregister(CancellableHelloEvent.class, handlerId);

        bus.dispatch(new CancellableHelloEvent("second"));
        assertEquals(1, listener.calls.size()); // Not called again
    }

    @Test
    void unregister_invalidHandlerId_doesNothing()
    {
        UnsubscribableListener listener = new UnsubscribableListener();
        bus.register(CancellableHelloEvent.class, listener,
                getMethod(listener, "handle"), 0);

        bus.unregister(CancellableHelloEvent.class, 999999L); // Invalid ID

        bus.dispatch(new CancellableHelloEvent("test"));
        assertEquals(1, listener.calls.size()); // Still handled
    }

    @Test
    void unregister_oneOfMultiple_onlyRemovesTarget()
    {
        UnsubscribableListener listener1 = new UnsubscribableListener();
        UnsubscribableListener listener2 = new UnsubscribableListener();

        long id1 = bus.register(CancellableHelloEvent.class, listener1,
                getMethod(listener1, "handle"), 0);
        long id2 = bus.register(CancellableHelloEvent.class, listener2,
                getMethod(listener2, "handle"), 0);

        bus.unregister(CancellableHelloEvent.class, id1);

        bus.dispatch(new CancellableHelloEvent("test"));

        assertEquals(0, listener1.calls.size());
        assertEquals(1, listener2.calls.size());
    }

    // ===== CANCEL TESTS =====

    @Listener
    static class CancellingListener
    {
        final List<String> calls = new ArrayList<>();

        @On(value = CancellableHelloEvent.class, priority = 10)
        public void cancel(CancellableHelloEvent event)
        {
            calls.add("cancel");
            event.cancel();
        }

        @On(value = CancellableHelloEvent.class, priority = 0)
        public void afterCancel(CancellableHelloEvent event)
        {
            calls.add("after");
        }
    }

    @Test
    void dispatch_whenEventCancelled_stopsPropagatesToLowerPriority()
    {
        CancellingListener listener = new CancellingListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        bus.dispatch(new CancellableHelloEvent("cancelled"));

        assertEquals(List.of("cancel"), listener.calls);
        assertFalse(listener.calls.contains("after"));
    }

    @Test
    void dispatch_cancellableEvent_notCancelled_callsAllHandlers()
    {
        CancellingListener listener = new CancellingListener();

        // Create a cancellable event but don't cancel it
        CancellableHelloEvent event = new CancellableHelloEvent("not-cancelled");

        // Manually register only the afterCancel handler
        bus.register(CancellableHelloEvent.class, listener,
                getMethod(listener, "afterCancel"), 10);

        bus.dispatch(event);

        assertTrue(listener.calls.contains("after"));
    }

    @Listener
    static class MultiCancelListener
    {
        final List<String> calls = new ArrayList<>();

        @On(value = CancellableHelloEvent.class, priority = 30)
        public void first(CancellableHelloEvent e) { calls.add("first"); }

        @On(value = CancellableHelloEvent.class, priority = 20)
        public void second(CancellableHelloEvent e) {
            calls.add("second");
            e.cancel();
        }

        @On(value = CancellableHelloEvent.class, priority = 10)
        public void third(CancellableHelloEvent e) { calls.add("third"); }

        @On(value = CancellableHelloEvent.class, priority = 0)
        public void fourth(CancellableHelloEvent e) { calls.add("fourth"); }
    }

    @Test
    void dispatch_cancelInMiddle_stopsAfterCancel()
    {
        MultiCancelListener listener = new MultiCancelListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        bus.dispatch(new CancellableHelloEvent("middle-cancel"));

        assertEquals(List.of("first", "second"), listener.calls);
    }

    @Test
    void dispatch_cancellableWithGlobalListeners_stopsDispatchToGlobalToo()
    {
        List<Event> globalEvents = new ArrayList<>();
        bus.subscribe(globalEvents::add);

        CancellingListener listener = new CancellingListener();
        EventListenerLoader.registerListeners(bus, List.of(listener));

        bus.dispatch(new CancellableHelloEvent("test"));

        // Global listeners should NOT receive cancelled events
        assertTrue(globalEvents.isEmpty());
    }

    // ===== HELPER =====

    private static Method getMethod(Object obj, String methodName) {
        try {
            return obj.getClass().getDeclaredMethod(methodName, CancellableHelloEvent.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}