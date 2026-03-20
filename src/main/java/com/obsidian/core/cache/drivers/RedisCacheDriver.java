package com.obsidian.core.cache.drivers;

import com.obsidian.core.cache.CacheDriver;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cache driver backed by Redis via a {@link JedisPool}. Batch operations use pipelining.
 */
public class RedisCacheDriver implements CacheDriver
{
    private final JedisPool pool;

    /**
     * Creates a new RedisCacheDriver with the given connection pool.
     *
     * @param pool The Jedis connection pool to use
     */
    public RedisCacheDriver(JedisPool pool)
    {
        this.pool = pool;
    }

    /**
     * Stores a value in Redis with a TTL.
     *
     * @param key The cache key
     * @param value The value to store
     * @param ttlSeconds The time-to-live in seconds
     */
    @Override
    public void put(String key, Object value, int ttlSeconds)
    {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttlSeconds, serialize(value));
        }
    }

    /**
     * Stores multiple values in Redis with a shared TTL using a pipeline.
     *
     * @param entries The key-value pairs to store
     * @param ttlSeconds The time-to-live in seconds
     */
    @Override
    public void putAll(Map<String, Object> entries, int ttlSeconds)
    {
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                pipeline.setex(entry.getKey(), ttlSeconds, serialize(entry.getValue()));
            }
            pipeline.sync();
        }
    }

    /**
     * Retrieves a value from Redis.
     *
     * @param key The cache key
     * @return The cached value, or null if not found or expired
     */
    @Override
    public Object get(String key)
    {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * Retrieves multiple values from Redis using a pipeline.
     *
     * @param keys The cache keys
     * @return A list of cached values in the same order as the keys, with null for missing entries
     */
    @Override
    public List<Object> getAll(List<String> keys)
    {
        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            List<Response<String>> responses = new ArrayList<>();
            for (String key : keys) {
                responses.add(pipeline.get(key));
            }
            pipeline.sync();
            List<Object> results = new ArrayList<>();
            for (Response<String> response : responses) {
                results.add(response.get());
            }
            return results;
        }
    }

    /**
     * Checks whether a key exists in Redis.
     *
     * @param key The cache key
     * @return True if the key exists, false otherwise
     */
    @Override
    public boolean has(String key)
    {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    /**
     * Removes a key from Redis.
     *
     * @param key The cache key to remove
     */
    @Override
    public void forget(String key)
    {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    /**
     * Removes all keys that start with the given prefix using SCAN.
     *
     * <p>Uses SCAN instead of KEYS to avoid blocking the Redis server on large keyspaces.</p>
     *
     * @param prefix The key prefix to match
     */
    @Override
    public void forgetByPrefix(String prefix)
    {
        try (Jedis jedis = pool.getResource()) {
            String cursor = "0";
            String pattern = prefix + "*";
            do {
                redis.clients.jedis.params.ScanParams params =
                        new redis.clients.jedis.params.ScanParams().match(pattern).count(100);
                redis.clients.jedis.resps.ScanResult<String> result = jedis.scan(cursor, params);
                cursor = result.getCursor();
                List<String> keys = result.getResult();
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
            } while (!cursor.equals("0"));
        }
    }

    /**
     * Serializes a value to a string for storage in Redis.
     *
     * @param value The value to serialize
     * @return The string representation of the value
     */
    private String serialize(Object value)
    {
        return value.toString();
    }
}