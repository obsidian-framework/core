package com.obsidian.core.queue;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FailedJobTest
{
    @Test
    void build_withAllFields_succeeds() {
        Instant now = Instant.now();
        FailedJob fj = FailedJob.builder()
                .id("fj-1")
                .queue("notifications")
                .jobClass("com.app.jobs.SendEmailJob")
                .payload("{}")
                .exception("java.lang.RuntimeException: boom")
                .totalAttempts(3)
                .failedAt(now)
                .build();

        assertEquals("fj-1", fj.getId());
        assertEquals("notifications", fj.getQueue());
        assertEquals("com.app.jobs.SendEmailJob", fj.getJobClass());
        assertEquals("{}", fj.getPayload());
        assertEquals("java.lang.RuntimeException: boom", fj.getException());
        assertEquals(3, fj.getTotalAttempts());
        assertEquals(now, fj.getFailedAt());
    }

    @Test
    void build_withoutFailedAt_defaultsToNow() {
        Instant before = Instant.now();
        FailedJob fj = FailedJob.builder().id("x").queue("q").jobClass("Foo").payload("{}").build();
        Instant after = Instant.now();

        assertNotNull(fj.getFailedAt());
        assertFalse(fj.getFailedAt().isBefore(before));
        assertFalse(fj.getFailedAt().isAfter(after));
    }

    @Test
    void build_withNullException_isAllowed() {
        FailedJob fj = FailedJob.builder()
                .id("x").queue("q").jobClass("Foo").payload("{}")
                .exception(null)
                .build();
        assertNull(fj.getException());
    }

    @Test
    void build_missingId_throwsNPE() {
        assertThrows(NullPointerException.class, () -> FailedJob.builder().queue("q").jobClass("Foo").payload("{}").build());
    }

    @Test
    void build_missingQueue_throwsNPE() {
        assertThrows(NullPointerException.class, () -> FailedJob.builder().id("x").jobClass("Foo").payload("{}").build());
    }

    @Test
    void build_missingJobClass_throwsNPE() {
        assertThrows(NullPointerException.class, () -> FailedJob.builder().id("x").queue("q").payload("{}").build());
    }

    @Test
    void build_missingPayload_throwsNPE() {
        assertThrows(NullPointerException.class, () -> FailedJob.builder().id("x").queue("q").jobClass("Foo").build());
    }

    // -------------------------------------------------------------------------
    // toString()
    // -------------------------------------------------------------------------

    @Test
    void toString_containsKeyInfo() {
        FailedJob fj = FailedJob.builder().id("fj-99").queue("default").jobClass("MyJob").payload("{}").build();
        String s = fj.toString();
        assertTrue(s.contains("fj-99"));
        assertTrue(s.contains("default"));
        assertTrue(s.contains("MyJob"));
    }
}