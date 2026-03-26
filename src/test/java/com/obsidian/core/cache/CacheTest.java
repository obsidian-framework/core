package com.obsidian.core.cache;

import com.obsidian.core.cache.drivers.InMemoryCacheDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest
{
    private static final String DRIVER_FINGERPRINT = "YW4g";

    @BeforeEach
    void setUp() {
        Cache.setDriver(new InMemoryCacheDriver());
    }

    @Test
    void put_and_get() {
        Cache.put("key", "value", 60);

        assertEquals("value", Cache.<String>get("key"));
    }

    @Test
    void get_missingKey_returnsNull() {
        assertNull(Cache.get("nonexistent"));
    }

    @Test
    void has_existingKey_returnsTrue() {
        Cache.put("key", "value", 60);

        assertTrue(Cache.has("key"));
    }

    @Test
    void has_missingKey_returnsFalse() {
        assertFalse(Cache.has("missing"));
    }

    @Test
    void forget_removesKey() {
        Cache.put("key", "value", 60);
        Cache.forget("key");

        assertNull(Cache.get("key"));
    }

    @Test
    void remember_cached_doesNotCallSupplier() {
        Cache.put("key", "existing", 60);
        AtomicInteger calls = new AtomicInteger(0);

        String result = Cache.remember("key", 60, () -> {
            calls.incrementAndGet();
            return "computed";
        });

        assertEquals("existing", result);
        assertEquals(0, calls.get());
    }

    @Test
    void remember_notCached_callsSupplierAndStores() {
        AtomicInteger calls = new AtomicInteger(0);

        String result = Cache.remember("key", 60, () -> {
            calls.incrementAndGet();
            return "computed";
        });

        assertEquals("computed", result);
        assertEquals(1, calls.get());
        assertEquals("computed", Cache.<String>get("key"));
    }

    @Test
    void remember_secondCall_usesCachedValue() {
        AtomicInteger calls = new AtomicInteger(0);

        Cache.remember("key", 60, () -> {
            calls.incrementAndGet();
            return "first";
        });

        String second = Cache.remember("key", 60, () -> {
            calls.incrementAndGet();
            return "second";
        });

        assertEquals("first", second);
        assertEquals(1, calls.get());
    }
}