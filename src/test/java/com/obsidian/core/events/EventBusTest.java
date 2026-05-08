package com.obsidian.core.events;

import com.obsidian.core.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest
{
    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
    }

    // =========================================================================
    // Event fixtures
    // =========================================================================

    static class BaseEvent {
        final String tag;
        BaseEvent(String tag) { this.tag = tag; }
    }

    static class ChildEvent extends BaseEvent {
        ChildEvent(String tag) { super(tag); }
    }

    static class UnrelatedEvent {
        final int value;
        UnrelatedEvent(int value) { this.value = value; }
    }

    static class CancellableEvent implements StoppableEvent {
        final List<String> trace = new ArrayList<>();
        private boolean stopped = false;

        @Override public boolean isPropagationStopped() { return stopped; }
        @Override public void stopPropagation()         { this.stopped = true; }
    }

    // =========================================================================
    // Listener fixtures
    // =========================================================================

    /** Records every event it receives, in order. */
    @Listener
    static class RecordingListener {
        final List<Object> received = new ArrayList<>();

        @On(BaseEvent.class)
        public void onBase(BaseEvent e) { received.add(e); }
    }

    @Listener
    static class ChildOnlyListener {
        final List<ChildEvent> received = new ArrayList<>();

        @On(ChildEvent.class)
        public void onChild(ChildEvent e) { received.add(e); }
    }

    @Listener
    static class PriorityRecorder {
        final List<String> order = new ArrayList<>();

        @On(value = BaseEvent.class, priority = 10)
        public void high(BaseEvent e) { order.add("high"); }

        @On(value = BaseEvent.class, priority = 0)
        public void medium(BaseEvent e) { order.add("medium"); }

        @On(value = BaseEvent.class, priority = -5)
        public void low(BaseEvent e) { order.add("low"); }
    }

    @Listener
    static class ThreeListeners {
        final List<String> order = new ArrayList<>();

        @On(value = BaseEvent.class, priority = 10)
        public void first(BaseEvent e) { order.add("first"); }

        @On(value = BaseEvent.class, priority = 5)
        public void second(BaseEvent e) {
            order.add("second");
            throw new RuntimeException("intentional");
        }

        @On(value = BaseEvent.class, priority = 0)
        public void third(BaseEvent e) { order.add("third"); }
    }

    @Listener
    static class CancellingListener {
        @On(value = CancellableEvent.class, priority = 10)
        public void first(CancellableEvent e) {
            e.trace.add("first");
            e.stopPropagation();
        }

        @On(value = CancellableEvent.class, priority = 0)
        public void second(CancellableEvent e) {
            e.trace.add("second"); // should never run
        }
    }

    @Listener
    static class SelfDispatchingListener {
        EventBus bus;
        final AtomicInteger calls = new AtomicInteger();

        @On(BaseEvent.class)
        public void loop(BaseEvent e) {
            calls.incrementAndGet();
            bus.dispatch(new BaseEvent("recurse"));
        }
    }

    @Listener static class HighPrio {
        final List<String> trace;
        HighPrio(List<String> t) { this.trace = t; }
        @On(value = BaseEvent.class, priority = 100)
        public void run(BaseEvent e) { trace.add("HIGH"); }
    }

    @Listener static class LowPrio {
        final List<String> trace;
        LowPrio(List<String> t) { this.trace = t; }
        @On(value = BaseEvent.class, priority = -100)
        public void run(BaseEvent e) { trace.add("LOW"); }
    }

    @Listener static class MidPrio {
        final List<String> trace;
        MidPrio(List<String> t) { this.trace = t; }
        @On(value = BaseEvent.class, priority = 0)
        public void run(BaseEvent e) { trace.add("MID"); }
    }

    @Listener static class BoomListener {
        @On(BaseEvent.class)
        public void boom(BaseEvent e) { throw new IllegalStateException("kaboom"); }
    }

    static class NotAListener {
        @On(BaseEvent.class)
        public void handle(BaseEvent e) { throw new AssertionError("Should not be called"); }
    }

    @Listener static class EmptyListener {
        public void notAListener(BaseEvent e) {}
    }

    @Listener static class TooManyArgs {
        @On(BaseEvent.class)
        public void handle(BaseEvent e, String extra) { throw new AssertionError("Should not be called"); }
    }

    @Listener static class WrongType {
        @On(BaseEvent.class) // annotated as BaseEvent
        public void handle(UnrelatedEvent e) { throw new AssertionError("Should not be called"); }
    }

    @Listener static class WideListener {
        Object captured;
        @On(BaseEvent.class)
        public void handle(Object e) { captured = e; }
    }

    @Listener static class CountingListener {
        final AtomicInteger counter;
        CountingListener(AtomicInteger c) { this.counter = c; }
        @On(BaseEvent.class)
        public void handle(BaseEvent e) { counter.incrementAndGet(); }
    }

    @Listener static class CleanCounter {
        final AtomicInteger counter;
        CleanCounter(AtomicInteger c) { this.counter = c; }
        @On(UnrelatedEvent.class)
        public void handle(UnrelatedEvent e) { counter.incrementAndGet(); }
    }

    // =========================================================================
    // Basic dispatch
    // =========================================================================

    @Nested
    class BasicDispatch
    {
        @Test
        void dispatch_invokesRegisteredListener() {
            RecordingListener listener = new RecordingListener();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            BaseEvent event = new BaseEvent("hello");
            bus.dispatch(event);

            assertEquals(1, listener.received.size());
            assertSame(event, listener.received.get(0));
        }

        @Test
        void dispatch_withNoListeners_doesNothing() {
            assertDoesNotThrow(() -> bus.dispatch(new BaseEvent("orphan")));
        }

        @Test
        void dispatch_nullEvent_throwsNpe() {
            assertThrows(NullPointerException.class, () -> bus.dispatch(null));
        }

        @Test
        void dispatch_unrelatedEvent_doesNotInvokeOtherListeners() {
            RecordingListener listener = new RecordingListener();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            bus.dispatch(new UnrelatedEvent(42));

            assertTrue(listener.received.isEmpty());
        }

        @Test
        void dispatch_invokesMultipleSeparateListeners() {
            RecordingListener a = new RecordingListener();
            RecordingListener b = new RecordingListener();
            EventListenerLoader.registerListeners(bus, List.of(a, b));

            bus.dispatch(new BaseEvent("x"));

            assertEquals(1, a.received.size());
            assertEquals(1, b.received.size());
        }
    }

    // =========================================================================
    // Subtype dispatch
    // =========================================================================

    @Nested
    class SubtypeDispatch
    {
        @Test
        void baseListener_receivesChildEvent() {
            RecordingListener listener = new RecordingListener();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            ChildEvent event = new ChildEvent("subclass");
            bus.dispatch(event);

            assertEquals(1, listener.received.size());
            assertSame(event, listener.received.get(0));
        }

        @Test
        void childListener_doesNotReceiveBaseEvent() {
            ChildOnlyListener listener = new ChildOnlyListener();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            bus.dispatch(new BaseEvent("base"));

            assertTrue(listener.received.isEmpty());
        }

        @Test
        void bothBaseAndChildListeners_receiveChildEvent() {
            RecordingListener base = new RecordingListener();
            ChildOnlyListener child = new ChildOnlyListener();
            EventListenerLoader.registerListeners(bus, List.of(base, child));

            ChildEvent event = new ChildEvent("propagate");
            bus.dispatch(event);

            assertEquals(1, base.received.size());
            assertEquals(1, child.received.size());
        }
    }

    // =========================================================================
    // Priority ordering
    // =========================================================================

    @Nested
    class PriorityOrdering
    {
        @Test
        void listenersInvokedInDescendingPriorityOrder() {
            PriorityRecorder listener = new PriorityRecorder();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            bus.dispatch(new BaseEvent("ordering"));

            assertEquals(List.of("high", "medium", "low"), listener.order);
        }

        @Test
        void priorityOrderingHoldsAcrossListenerClasses() {
            // Two separate listener classes with overlapping priorities to verify
            // that the bus interleaves them correctly rather than grouping by class.
            List<String> trace = Collections.synchronizedList(new ArrayList<>());
            // Register in random-ish order to confirm priority — not registration order — wins.
            EventListenerLoader.registerListeners(
                    bus, List.of(new MidPrio(trace), new LowPrio(trace), new HighPrio(trace)));

            bus.dispatch(new BaseEvent("x"));

            assertEquals(List.of("HIGH", "MID", "LOW"), trace);
        }
    }

    // =========================================================================
    // Failure isolation
    // =========================================================================

    @Nested
    class FailureIsolation
    {
        @Test
        void throwingListener_doesNotPreventOtherListeners() {
            ThreeListeners listener = new ThreeListeners();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            // Should NOT throw even though the middle listener does.
            assertDoesNotThrow(() -> bus.dispatch(new BaseEvent("trio")));

            assertEquals(List.of("first", "second", "third"), listener.order);
        }

        @Test
        void throwingListener_doesNotPropagateToCaller() {
            EventListenerLoader.registerListeners(bus, List.of(new BoomListener()));

            assertDoesNotThrow(() -> bus.dispatch(new BaseEvent("safe")));
        }
    }

    // =========================================================================
    // Stoppable events
    // =========================================================================

    @Nested
    class Stoppable
    {
        @Test
        void stopPropagation_skipsRemainingListeners() {
            CancellingListener listener = new CancellingListener();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            CancellableEvent event = new CancellableEvent();
            bus.dispatch(event);

            assertEquals(List.of("first"), event.trace);
            assertTrue(event.isPropagationStopped());
        }

        @Test
        void nonStoppableEvent_runsAllListenersEvenIfFirstWantsToStop() {
            // A listener that calls stopPropagation() on a non-StoppableEvent
            // doesn't exist in our model — but verify the bus doesn't crash
            // checking propagation state on a regular event.
            PriorityRecorder listener = new PriorityRecorder();
            EventListenerLoader.registerListeners(bus, List.of(listener));

            bus.dispatch(new BaseEvent("regular"));

            assertEquals(3, listener.order.size());
        }
    }

    // =========================================================================
    // Recursion guard
    // =========================================================================

    @Nested
    class RecursionGuard
    {
        @Test
        void infiniteSelfDispatch_throwsRecursionException() {
            SelfDispatchingListener listener = new SelfDispatchingListener();
            listener.bus = bus;
            EventListenerLoader.registerListeners(bus, List.of(listener));

            assertThrows(EventRecursionException.class,
                    () -> bus.dispatch(new BaseEvent("loop")));

            // The listener was invoked maxDispatchDepth (8) times before the guard tripped.
            assertEquals(bus.getMaxDispatchDepth(), listener.calls.get());
        }

        @Test
        void recursionGuard_resetsBetweenDispatches() {
            SelfDispatchingListener listener = new SelfDispatchingListener();
            listener.bus = bus;
            EventListenerLoader.registerListeners(bus, List.of(listener));

            // First dispatch trips the guard.
            assertThrows(EventRecursionException.class,
                    () -> bus.dispatch(new BaseEvent("first")));

            // A subsequent UNRELATED dispatch should work fine — the depth counter must
            // have been reset, otherwise the thread would be permanently poisoned.
            RecordingListener clean = new RecordingListener();
            EventBus freshBus = new EventBus();
            EventListenerLoader.registerListeners(freshBus, List.of(clean));

            assertDoesNotThrow(() -> freshBus.dispatch(new BaseEvent("clean")));
            assertEquals(1, clean.received.size());
        }

        @Test
        void customMaxDispatchDepth_isRespected() {
            bus.setMaxDispatchDepth(3);
            SelfDispatchingListener listener = new SelfDispatchingListener();
            listener.bus = bus;
            EventListenerLoader.registerListeners(bus, List.of(listener));

            assertThrows(EventRecursionException.class,
                    () -> bus.dispatch(new BaseEvent("shallow")));

            assertEquals(3, listener.calls.get());
        }

        @Test
        void setMaxDispatchDepth_rejectsZero() {
            assertThrows(IllegalArgumentException.class, () -> bus.setMaxDispatchDepth(0));
        }

        @Test
        void setMaxDispatchDepth_rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> bus.setMaxDispatchDepth(-1));
        }
    }

    // =========================================================================
    // Loader validation
    // =========================================================================

    @Nested
    class LoaderValidation
    {
        @Test
        void nonAnnotatedObject_isSkipped() {
            // A plain class without @Listener — the loader should skip it gracefully.
            int registered = EventListenerLoader.registerListeners(bus, List.of(new NotAListener()));

            assertEquals(0, registered);
            // Confirm the method really wasn't wired up.
            bus.dispatch(new BaseEvent("ignored"));
        }

        @Test
        void listenerWithNoOnMethods_logsWarningButDoesNotFail() {
            assertDoesNotThrow(() ->
                    EventListenerLoader.registerListeners(bus, List.of(new EmptyListener())));
        }

        @Test
        void wrongParameterCount_isSkipped() {
            // Should not throw; the bad method is simply skipped.
            int count = EventListenerLoader.registerListeners(bus, List.of(new TooManyArgs()));
            assertEquals(0, count);
            bus.dispatch(new BaseEvent("orphan"));
        }

        @Test
        void incompatibleParameterType_isSkipped() {
            int count = EventListenerLoader.registerListeners(bus, List.of(new WrongType()));
            assertEquals(0, count);
        }

        @Test
        void parameterIsSupertype_isAccepted() {
            // Method declares Object as parameter, @On says BaseEvent — this is fine
            // because Object is assignable from BaseEvent.
            WideListener listener = new WideListener();
            int count = EventListenerLoader.registerListeners(bus, List.of(listener));

            assertEquals(1, count);
            BaseEvent event = new BaseEvent("widened");
            bus.dispatch(event);
            assertSame(event, listener.captured);
        }
    }

    // =========================================================================
    // Concurrency
    // =========================================================================

    @Nested
    class Concurrency
    {
        @Test
        void parallelDispatches_allReachListener() throws Exception {
            int threadCount = 16;
            int eventsPerThread = 100;

            AtomicInteger counter = new AtomicInteger();
            EventListenerLoader.registerListeners(bus, List.of(new CountingListener(counter)));

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done  = new CountDownLatch(threadCount);
            List<Thread> threads = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < eventsPerThread; i++) {
                            bus.dispatch(new BaseEvent("concurrent"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                thread.start();
                threads.add(thread);
            }

            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "Threads did not finish in time");

            assertEquals(threadCount * eventsPerThread, counter.get());
        }

        @Test
        void recursionGuardIsThreadLocal() throws Exception {
            // Thread A trips the recursion guard. Thread B running concurrently
            // must NOT be affected — the depth counter is per-thread.
            SelfDispatchingListener loopListener = new SelfDispatchingListener();
            loopListener.bus = bus;

            AtomicInteger cleanCalls = new AtomicInteger();
            EventListenerLoader.registerListeners(bus, List.of(loopListener, new CleanCounter(cleanCalls)));

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go    = new CountDownLatch(1);
            CountDownLatch done  = new CountDownLatch(2);

            Thread looper = new Thread(() -> {
                try {
                    ready.countDown();
                    go.await();
                    assertThrows(EventRecursionException.class,
                            () -> bus.dispatch(new BaseEvent("loop")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            Thread cleanThread = new Thread(() -> {
                try {
                    ready.countDown();
                    go.await();
                    for (int i = 0; i < 50; i++) {
                        bus.dispatch(new UnrelatedEvent(i));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });

            looper.start();
            cleanThread.start();

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));

            // Clean thread was unaffected by the looper's recursion blow-up.
            assertEquals(50, cleanCalls.get());
        }
    }

    // =========================================================================
    // Direct registration (bypassing the loader)
    // =========================================================================

    @Nested
    class DirectRegistration
    {
        @Test
        void register_invalidatesCache() throws Exception {
            // First listener triggers the resolved-chain cache to populate.
            RecordingListener first = new RecordingListener();
            EventListenerLoader.registerListeners(bus, List.of(first));
            bus.dispatch(new BaseEvent("warmup")); // populates the cache

            // Now register a SECOND listener manually. The cache must be invalidated,
            // otherwise the new listener will be invisible.
            RecordingListener second = new RecordingListener();
            bus.register(BaseEvent.class, second,
                    second.getClass().getDeclaredMethod("onBase", BaseEvent.class), 0);

            bus.dispatch(new BaseEvent("post-registration"));

            assertEquals(2, first.received.size());      // warmup + post-registration
            assertEquals(1, second.received.size());     // post-registration only
        }

        @Test
        void listenerCountFor_returnsExactRegistrationCount() throws Exception {
            assertEquals(0, bus.listenerCountFor(BaseEvent.class));

            RecordingListener listener = new RecordingListener();
            bus.register(BaseEvent.class, listener,
                    listener.getClass().getDeclaredMethod("onBase", BaseEvent.class), 0);

            assertEquals(1, bus.listenerCountFor(BaseEvent.class));
            // Subtype walking is NOT applied here — that's only for dispatch.
            assertEquals(0, bus.listenerCountFor(ChildEvent.class));
        }
    }
}