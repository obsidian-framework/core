package com.obsidian.core.queue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobTest
{
    static class SimpleJob implements Job {
        boolean handled = false;
        boolean failedCalled = false;
        Throwable failedCause = null;
        private final boolean shouldThrow;

        SimpleJob() { this.shouldThrow = false; }
        SimpleJob(boolean shouldThrow) { this.shouldThrow = shouldThrow; }

        @Override
        public void handle() throws Exception {
            if (shouldThrow) throw new RuntimeException("boom");
            handled = true;
        }
    }

    static class CustomAttemptsJob implements Job {
        @Override public void handle() {}
        @Override public int maxAttempts() { return 5; }
        @Override public int retryDelay()  { return 30; }
        @Override public void onFailed(Throwable cause) {}
    }

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    void defaultMaxAttempts_isThree() {
        assertEquals(3, new SimpleJob().maxAttempts());
    }

    @Test
    void defaultRetryDelay_isSixtySeconds() {
        assertEquals(60, new SimpleJob().retryDelay());
    }

    @Test
    void defaultOnFailed_doesNotThrow() {
        assertDoesNotThrow(() -> new SimpleJob().onFailed(new RuntimeException("x")));
    }

    // -------------------------------------------------------------------------
    // Custom overrides
    // -------------------------------------------------------------------------

    @Test
    void customMaxAttempts_isRespected() {
        assertEquals(5, new CustomAttemptsJob().maxAttempts());
    }

    @Test
    void customRetryDelay_isRespected() {
        assertEquals(30, new CustomAttemptsJob().retryDelay());
    }

    // -------------------------------------------------------------------------
    // handle()
    // -------------------------------------------------------------------------

    @Test
    void handle_executesSuccessfully() throws Exception {
        SimpleJob job = new SimpleJob();
        job.handle();
        assertTrue(job.handled);
    }

    @Test
    void handle_propagatesException() {
        SimpleJob job = new SimpleJob(true);
        assertThrows(RuntimeException.class, job::handle);
    }
}