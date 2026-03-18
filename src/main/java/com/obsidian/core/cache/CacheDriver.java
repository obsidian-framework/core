package com.obsidian.core.cache;

import java.util.List;
import java.util.Map;

/**
 * Contract for cache drivers.
 */
public interface CacheDriver
{
    /**
     * Stores a value in the cache with a TTL.
     *
     * @param key The cache key
     * @param value The value to store
     * @param ttlSeconds The time-to-live in seconds
     */
    void put(String key, Object value, int ttlSeconds);

    /**
     * Stores multiple values in the cache with a shared TTL.
     *
     * @param entries The key-value pairs to store
     * @param ttlSeconds The time-to-live in seconds
     */
    void putAll(Map<String, Object> entries, int ttlSeconds);

    /**
     * Retrieves a value from the cache.
     *
     * @param key The cache key
     * @return The cached value, or null if not found or expired
     */
    Object get(String key);

    /**
     * Retrieves multiple values from the cache.
     *
     * @param keys The cache keys
     * @return A list of cached values in the same order as the keys, with null for missing entries
     */
    List<Object> getAll(List<String> keys);

    /**
     * Checks whether a key exists and has not expired.
     *
     * @param key The cache key
     * @return True if the key exists and is valid, false otherwise
     */
    boolean has(String key);

    /**
     * Removes a key from the cache.
     *
     * @param key The cache key to remove
     */
    void forget(String key);
}