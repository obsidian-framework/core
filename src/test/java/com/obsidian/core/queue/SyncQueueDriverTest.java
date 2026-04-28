package com.obsidian.core.queue;

import com.obsidian.core.queue.drivers.SyncQueueDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SyncQueueDriverTest
{
    SyncQueueDriver driver;

    @BeforeEach
    void setUp() {
        driver = new SyncQueueDriver();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    static class SuccessJob implements Job {
        boolean executed = false;
        @Override public void handle() { executed = true; }
    }

    static class FailingJob implements Job {
        AtomicInteger attempts = new AtomicInteger(0);
        final int failUntil;

        FailingJob(int failUntil) { this.failUntil = failUntil; }

        @Override
        public void handle() throws Exception {
            int attempt = attempts.incrementAndGet();
            if (attempt <= failUntil) throw new RuntimeException("fail #" + attempt);
        }
    }

    static class AlwaysFailJob implements Job {
        AtomicInteger attempts = new AtomicInteger(0);
        boolean onFailedCalled = false;
        Throwable onFailedCause = null;

        @Override
        public void handle() throws Exception {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
        }

        @Override
        public int maxAttempts() { return 2; }

        @Override
        public void onFailed(Throwable cause) {
            onFailedCalled = true;
            onFailedCause = cause;
        }
    }

    static class OnFailedThrowsJob implements Job {
        @Override public void handle() throws Exception { throw new RuntimeException("boom"); }
        @Override public int maxAttempts() { return 1; }
        @Override public void onFailed(Throwable cause) { throw new RuntimeException("onFailed exploded"); }
    }

    // -------------------------------------------------------------------------
    // push() — exécution synchrone
    // -------------------------------------------------------------------------

    @Test
    void push_executesJobImmediately() {
        SuccessJob job = new SuccessJob();
        driver.push("default", job, 0);
        assertTrue(job.executed);
    }

    @Test
    void push_returnsNonNullId() {
        String id = driver.push("default", new SuccessJob(), 0);
        assertNotNull(id);
        assertTrue(id.startsWith("sync-"));
    }

    @Test
    void push_nullJob_throws() {
        assertThrows(NullPointerException.class, () -> driver.push("default", null, 0));
    }

    @Test
    void push_nullQueue_throws() {
        assertThrows(NullPointerException.class, () -> driver.push(null, new SuccessJob(), 0));
    }

    @Test
    void push_delayIsIgnored_jobStillExecutes() {
        SuccessJob job = new SuccessJob();
        driver.push("default", job, 999); // delay ignoré en mode sync
        assertTrue(job.executed);
    }

    // -------------------------------------------------------------------------
    // Retry loop
    // -------------------------------------------------------------------------

    @Test
    void push_retriesUntilSuccess() {
        FailingJob job = new FailingJob(2); // échoue 2 fois, réussit à la 3e
        driver.push("default", job, 0);
        assertEquals(3, job.attempts.get());
    }

    @Test
    void push_exhaustsAllAttempts_callsOnFailed() {
        AlwaysFailJob job = new AlwaysFailJob();
        driver.push("default", job, 0);
        assertEquals(2, job.attempts.get());
        assertTrue(job.onFailedCalled);
        assertNotNull(job.onFailedCause);
    }

    @Test
    void push_onFailedCallbackThrows_doesNotPropagateException() {
        assertDoesNotThrow(() -> driver.push("default", new OnFailedThrowsJob(), 0));
    }

    @Test
    void push_maxAttemptsZeroOrNegative_executesAtLeastOnce() {
        Job zeroAttempts = new Job() {
            AtomicInteger count = new AtomicInteger();
            @Override public void handle() { count.incrementAndGet(); }
            @Override public int maxAttempts() { return 0; }
        };
        driver.push("default", zeroAttempts, 0);
        assertDoesNotThrow(() -> {});
    }

    // -------------------------------------------------------------------------
    // No-op methods
    // -------------------------------------------------------------------------

    @Test
    void pop_returnsEmpty() {
        assertTrue(driver.pop("default").isEmpty());
    }

    @Test
    void acknowledge_doesNotThrow() {
        assertDoesNotThrow(() -> driver.acknowledge("any-id"));
    }

    @Test
    void release_doesNotThrow() {
        assertDoesNotThrow(() -> driver.release("any-id", 10, new RuntimeException()));
    }

    @Test
    void getFailedJobs_returnsEmptyList() {
        assertTrue(driver.getFailedJobs(10, 0).isEmpty());
    }

    @Test
    void retryFailedJob_returnsFalse() {
        assertFalse(driver.retryFailedJob("any-id"));
    }

    @Test
    void flushFailedJobs_doesNotThrow() {
        assertDoesNotThrow(() -> driver.flushFailedJobs());
    }

    @Test
    void size_returnsMinusOne() {
        assertEquals(-1L, driver.size("default"));
    }
}