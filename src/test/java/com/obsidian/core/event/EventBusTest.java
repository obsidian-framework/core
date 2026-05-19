package com.obsidian.core.event;

import com.obsidian.core.event.annotations.Listener;
import com.obsidian.core.event.annotations.On;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}