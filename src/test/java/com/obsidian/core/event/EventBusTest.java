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

    static class HelloEvent
    {
        final String message;
        HelloEvent(String message) { this.message = message; }
    }

    static class OtherEvent {}

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
}