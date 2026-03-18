package com.obsidian.core.cache;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Static facade for the active cache driver.
 */
public class Cache
{
    private static CacheDriver driver;

    /**
     * Sets the active cache driver.
     *
     * @param driver The driver to use
     */
    public static void setDriver(CacheDriver driver)
    {
        Cache.driver = driver;
    }

    /**
     * Stores a value in the cache with a TTL.
     *
     * @param key The cache key
     * @param value The value to store
     * @param ttlSeconds The time-to-live in seconds
     */
    public static void put(String key, Object value, int ttlSeconds)
    {
        driver.put(key, value, ttlSeconds);
    }

    /**
     * Stores multiple values in the cache with a shared TTL.
     *
     * @param entries The key-value pairs to store
     * @param ttlSeconds The time-to-live in seconds
     */
    public static void putAll(Map<String, Object> entries, int ttlSeconds)
    {
        driver.putAll(entries, ttlSeconds);
    }

    /**
     * Retrieves a value from the cache.
     *
     * @param key The cache key
     * @return The cached value cast to T, or null if not found or expired
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key)
    {
        return (T) driver.get(key);
    }

    /**
     * Retrieves multiple values from the cache.
     *
     * @param keys The cache keys
     * @return A list of cached values cast to T in the same order as the keys
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getAll(List<String> keys)
    {
        return (List<T>) driver.getAll(keys);
    }

    /**
     * Checks whether a key exists and has not expired.
     *
     * @param key The cache key
     * @return True if the key exists and is valid, false otherwise
     */
    public static boolean has(String key)
    {
        return driver.has(key);
    }

    /**
     * Removes a key from the cache.
     *
     * @param key The cache key to remove
     */
    public static void forget(String key)
    {
        driver.forget(key);
    }

    /**
     * Returns the cached value if present, otherwise computes, stores, and returns it.
     *
     * @param key The cache key
     * @param ttlSeconds The time-to-live in seconds if the value is computed
     * @param supplier The function to compute the value if not cached
     * @return The cached or freshly computed value
     */
    public static <T> T remember(String key, int ttlSeconds, Supplier<T> supplier)
    {
        T cached = get(key);
        if (cached != null) return cached;
        T value = supplier.get();
        put(key, value, ttlSeconds);
        return value;
    }
}