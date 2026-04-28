package com.obsidian.core.queue;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class QueuedJobTest
{
    static final Job DUMMY_JOB = new Job() {
        @Override public void handle() {}
    };

    static final Job EXHAUSTED_JOB = new Job() {
        @Override public void handle() {}
        @Override public int maxAttempts() { return 2; }
    };

    @Test
    void build_withAllFields_succeeds() {
        QueuedJob qj = QueuedJob.builder()
                .id("abc")
                .queue("default")
                .job(DUMMY_JOB)
                .attempts(1)
                .availableAt(Instant.now())
                .reservedAt(Instant.now())
                .build();

        assertEquals("abc", qj.getId());
        assertEquals("default", qj.getQueue());
        assertEquals(DUMMY_JOB, qj.getJob());
        assertEquals(1, qj.getAttempts());
        assertNotNull(qj.getAvailableAt());
        assertNotNull(qj.getReservedAt());
    }

    @Test
    void build_withoutReservedAt_defaultsToNow() {
        Instant before = Instant.now();
        QueuedJob qj = QueuedJob.builder()
                .id("x").queue("q").job(DUMMY_JOB)
                .attempts(0).availableAt(Instant.now())
                .build();
        Instant after = Instant.now();

        assertNotNull(qj.getReservedAt());
        assertFalse(qj.getReservedAt().isBefore(before));
        assertFalse(qj.getReservedAt().isAfter(after));
    }

    @Test
    void build_missingId_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                QueuedJob.builder().queue("q").job(DUMMY_JOB).availableAt(Instant.now()).build());
    }

    @Test
    void build_missingQueue_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                QueuedJob.builder().id("x").job(DUMMY_JOB).availableAt(Instant.now()).build());
    }

    @Test
    void build_missingJob_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                QueuedJob.builder().id("x").queue("q").availableAt(Instant.now()).build());
    }

    @Test
    void build_missingAvailableAt_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                QueuedJob.builder().id("x").queue("q").job(DUMMY_JOB).build());
    }

    // -------------------------------------------------------------------------
    // isExhausted()
    // -------------------------------------------------------------------------

    @Test
    void isExhausted_whenAttemptsLessThanMax_returnsFalse() {
        QueuedJob qj = baseBuilder().job(EXHAUSTED_JOB).attempts(1).build();
        assertFalse(qj.isExhausted());
    }

    @Test
    void isExhausted_whenAttemptsEqualsMax_returnsTrue() {
        QueuedJob qj = baseBuilder().job(EXHAUSTED_JOB).attempts(2).build();
        assertTrue(qj.isExhausted());
    }

    @Test
    void isExhausted_whenAttemptsExceedsMax_returnsTrue() {
        QueuedJob qj = baseBuilder().job(EXHAUSTED_JOB).attempts(99).build();
        assertTrue(qj.isExhausted());
    }

    @Test
    void isExhausted_defaultMaxAttempts_notExhaustedAtTwo() {
        QueuedJob qj = baseBuilder().job(DUMMY_JOB).attempts(2).build();
        assertFalse(qj.isExhausted()); // maxAttempts = 3
    }

    // -------------------------------------------------------------------------
    // toString()
    // -------------------------------------------------------------------------

    @Test
    void toString_containsIdAndQueue() {
        QueuedJob qj = baseBuilder().id("myid").queue("myqueue").build();
        String s = qj.toString();
        assertTrue(s.contains("myid"));
        assertTrue(s.contains("myqueue"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private QueuedJob.Builder baseBuilder() {
        return QueuedJob.builder()
                .id("id").queue("default").job(DUMMY_JOB)
                .attempts(0).availableAt(Instant.now());
    }
}