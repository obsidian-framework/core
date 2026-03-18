package com.obsidian.core.cache.drivers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCacheDriverTest
{
    private InMemoryCacheDriver cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheDriver();
    }

    // ──────────────────────────────────────────────
    // put / get
    // ──────────────────────────────────────────────

    @Test
    void put_and_get_returnsValue() {
        cache.put("key", "value", 60);

        assertEquals("value", cache.get("key"));
    }

    @Test
    void get_missingKey_returnsNull() {
        assertNull(cache.get("nonexistent"));
    }

    @Test
    void put_overwritesExisting() {
        cache.put("key", "old", 60);
        cache.put("key", "new", 60);

        assertEquals("new", cache.get("key"));
    }

    @Test
    void put_differentTypes() {
        cache.put("string", "hello", 60);
        cache.put("int", 42, 60);
        cache.put("list", List.of(1, 2, 3), 60);

        assertEquals("hello", cache.get("string"));
        assertEquals(42, cache.get("int"));
        assertEquals(List.of(1, 2, 3), cache.get("list"));
    }

    // ──────────────────────────────────────────────
    // TTL expiry
    // ──────────────────────────────────────────────

    @Test
    void get_expiredKey_returnsNull() throws InterruptedException {
        cache.put("key", "value", 1); // 1 second TTL

        Thread.sleep(1100); // Wait for expiry

        assertNull(cache.get("key"));
    }

    @Test
    void has_expiredKey_returnsFalse() throws InterruptedException {
        cache.put("key", "value", 1);

        Thread.sleep(1100);

        assertFalse(cache.has("key"));
    }

    // ──────────────────────────────────────────────
    // has
    // ──────────────────────────────────────────────

    @Test
    void has_existingKey_returnsTrue() {
        cache.put("key", "value", 60);

        assertTrue(cache.has("key"));
    }

    @Test
    void has_missingKey_returnsFalse() {
        assertFalse(cache.has("nonexistent"));
    }

    // ──────────────────────────────────────────────
    // forget
    // ──────────────────────────────────────────────

    @Test
    void forget_removesKey() {
        cache.put("key", "value", 60);
        cache.forget("key");

        assertNull(cache.get("key"));
        assertFalse(cache.has("key"));
    }

    @Test
    void forget_nonexistentKey_doesNotThrow() {
        assertDoesNotThrow(() -> cache.forget("nope"));
    }

    // ──────────────────────────────────────────────
    // putAll / getAll
    // ──────────────────────────────────────────────

    @Test
    void putAll_storesMultipleEntries() {
        cache.putAll(Map.of("a", 1, "b", 2, "c", 3), 60);

        assertEquals(1, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(3, cache.get("c"));
    }

    @Test
    void getAll_returnsValuesInOrder() {
        cache.put("x", "X", 60);
        cache.put("y", "Y", 60);
        cache.put("z", "Z", 60);

        List<Object> results = cache.getAll(List.of("x", "y", "z"));

        assertEquals(List.of("X", "Y", "Z"), results);
    }

    @Test
    void getAll_missingKeys_returnsNulls() {
        cache.put("x", "X", 60);

        List<Object> results = cache.getAll(List.of("x", "missing"));

        assertEquals("X", results.get(0));
        assertNull(results.get(1));
    }
}