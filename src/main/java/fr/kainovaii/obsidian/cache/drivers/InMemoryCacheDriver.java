package fr.kainovaii.obsidian.cache.drivers;

import fr.kainovaii.obsidian.cache.CacheDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache driver backed by a {@link ConcurrentHashMap} with TTL-based expiry.
 */
public class InMemoryCacheDriver implements CacheDriver
{
    private record Entry(Object value, long expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * Stores a value in the cache with a TTL.
     *
     * @param key The cache key
     * @param value The value to store
     * @param ttlSeconds The time-to-live in seconds
     */
    @Override
    public void put(String key, Object value, int ttlSeconds)
    {
        long expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
        store.put(key, new Entry(value, expiresAt));
    }

    /**
     * Stores multiple values in the cache with a shared TTL.
     *
     * @param entries The key-value pairs to store
     * @param ttlSeconds The time-to-live in seconds
     */
    @Override
    public void putAll(Map<String, Object> entries, int ttlSeconds)
    {
        entries.forEach((key, value) -> put(key, value, ttlSeconds));
    }

    /**
     * Retrieves a value from the cache, removing it if expired.
     *
     * @param key The cache key
     * @return The cached value, or null if not found or expired
     */
    @Override
    public Object get(String key)
    {
        Entry entry = store.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    /**
     * Retrieves multiple values from the cache.
     *
     * @param keys The cache keys
     * @return A list of cached values in the same order as the keys, with null for missing or expired entries
     */
    @Override
    public List<Object> getAll(List<String> keys)
    {
        List<Object> results = new ArrayList<>();
        for (String key : keys) {
            results.add(get(key));
        }
        return results;
    }

    /**
     * Checks whether a key exists and has not expired.
     *
     * @param key The cache key
     * @return True if the key exists and is valid, false otherwise
     */
    @Override
    public boolean has(String key)
    {
        return get(key) != null;
    }

    /**
     * Removes a key from the cache.
     *
     * @param key The cache key to remove
     */
    @Override
    public void forget(String key)
    {
        store.remove(key);
    }
}