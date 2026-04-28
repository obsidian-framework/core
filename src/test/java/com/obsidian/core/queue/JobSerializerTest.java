package com.obsidian.core.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobSerializerTest
{
    // -------------------------------------------------------------------------
    // Concrete Job fixtures
    // -------------------------------------------------------------------------

    static class HelloJob implements Job {
        private String message;

        // Required by Jackson
        public HelloJob() {}
        public HelloJob(String message) { this.message = message; }

        public String getMessage() { return message; }
        public void setMessage(String m) { this.message = m; }

        @Override public void handle() {}
    }

    static class AnotherJob implements Job {
        private int count;
        public AnotherJob() {}
        public AnotherJob(int count) { this.count = count; }
        public int getCount() { return count; }
        public void setCount(int c) { this.count = c; }
        @Override public void handle() {}
    }

    @BeforeEach
    void resetSerializer() throws Exception {
        // Reset the static mapper between tests via reflection
        var mapperField = JobSerializer.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(null, null);

        var pkgField = JobSerializer.class.getDeclaredField("allowedPackages");
        pkgField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> pkgs = (java.util.Set<String>) pkgField.get(null);
        pkgs.clear();
        pkgs.add("com.obsidian"); // restore default

        var clsField = JobSerializer.class.getDeclaredField("allowedClasses");
        clsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> cls = (java.util.Set<String>) clsField.get(null);
        cls.clear();
    }

    // -------------------------------------------------------------------------
    // serialize()
    // -------------------------------------------------------------------------

    @Test
    void serialize_producesValidJson() {
        String json = JobSerializer.serialize(new HelloJob("world"));
        assertNotNull(json);
        assertTrue(json.contains("world"));
        assertTrue(json.contains("@class"));
    }

    // -------------------------------------------------------------------------
    // deserialize()
    // -------------------------------------------------------------------------

    @Test
    void roundtrip_preservesFields() {
        HelloJob original = new HelloJob("hello queue");
        String json = JobSerializer.serialize(original);
        Job result = JobSerializer.deserialize(json);

        assertInstanceOf(HelloJob.class, result);
        assertEquals("hello queue", ((HelloJob) result).getMessage());
    }

    @Test
    void roundtrip_differentJobTypes() {
        AnotherJob original = new AnotherJob(42);
        String json = JobSerializer.serialize(original);
        Job result = JobSerializer.deserialize(json);

        assertInstanceOf(AnotherJob.class, result);
        assertEquals(42, ((AnotherJob) result).getCount());
    }

    @Test
    void deserialize_invalidJson_throwsQueueException() {
        assertThrows(QueueException.class, () -> JobSerializer.deserialize("not-valid-json"));
    }

    @Test
    void deserialize_emptyString_throwsQueueException() {
        assertThrows(QueueException.class, () -> JobSerializer.deserialize(""));
    }

    // -------------------------------------------------------------------------
    // allowPackage() / allowClass()
    // -------------------------------------------------------------------------

    @Test
    void allowPackage_blankInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> JobSerializer.allowPackage("  "));
    }

    @Test
    void allowPackage_nullInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> JobSerializer.allowPackage(null));
    }

    @Test
    void allowClass_blankInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> JobSerializer.allowClass(""));
    }

    @Test
    void allowClass_nullInput_throws() {
        assertThrows(IllegalArgumentException.class, () -> JobSerializer.allowClass(null));
    }

    @Test
    void allowPackage_invalidatesMapper() throws Exception {
        // Trigger mapper creation
        JobSerializer.serialize(new HelloJob("x"));

        var mapperField = JobSerializer.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        assertNotNull(mapperField.get(null));

        JobSerializer.allowPackage("com.other");

        assertNull(mapperField.get(null));
    }
}