package com.obsidian.core.cache;

import com.obsidian.core.cache.drivers.InMemoryCacheDriver;
import com.obsidian.core.cache.drivers.RedisCacheDriver;
import com.obsidian.core.core.EnvKeys;
import com.obsidian.core.core.EnvLoader;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Initializes and registers the appropriate cache driver from environment configuration.
 */
public class CacheLoader
{
    /**
     * Reads the CACHE_DRIVER environment variable and registers the matching driver on {@link Cache}.
     * Defaults to {@link InMemoryCacheDriver} if the driver is not set to "redis".
     */
    public static void loadCache()
    {
        String driver = EnvLoader.getInstance().get(EnvKeys.CACHE_DRIVER);
        if ("redis".equalsIgnoreCase(driver)) {
            String host = EnvLoader.getInstance().get("REDIS_HOST");
            int port = Integer.parseInt(EnvLoader.getInstance().get("REDIS_PORT"));
            String password = EnvLoader.getInstance().get("REDIS_PASSWORD");
            JedisPool pool = new JedisPool(new JedisPoolConfig(), host, port, 2000, password);
            Cache.setDriver(new RedisCacheDriver(pool));
        } else {
            Cache.setDriver(new InMemoryCacheDriver());
        }
    }
}