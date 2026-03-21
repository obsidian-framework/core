package com.obsidian.core.database.orm.model;

import com.obsidian.core.cache.Cache;

import java.util.List;
import java.util.function.Supplier;

/**
 * ORM-level cache manager.
 */
public final class ModelCache {

    private ModelCache() {}

    /**
     * Returns a cache key for a single row looked up by primary key.
     *
     * @param modelClass model class
     * @param id         primary key value
     * @return cache key string
     */
    static String pkKey(Class<?> modelClass, Object id) {
        return "orm:" + modelClass.getSimpleName() + ":pk:" + id;
    }

    /**
     * Returns a cache key derived from a query's SQL and bindings.
     *
     * @param modelClass model class
     * @param sql        raw SQL string
     * @param bindings   query parameter bindings
     * @return cache key string
     */
    static String queryKey(Class<?> modelClass, String sql, List<Object> bindings) {
        int hash = 31 * sql.hashCode() + bindings.hashCode();
        return "orm:" + modelClass.getSimpleName() + ":q:" + Integer.toHexString(hash);
    }

    /**
     * Returns the key prefix covering all cache entries for a model class.
     *
     * @param modelClass model class
     * @return cache key prefix
     */
    static String prefix(Class<?> modelClass) {
        return "orm:" + modelClass.getSimpleName() + ":";
    }

    /**
     * Returns the cached value for {@code key} if present,
     * otherwise computes via {@code loader}, stores it, and returns it.
     *
     * @param modelClass model class, used to resolve TTL
     * @param key        cache key
     * @param loader     called on cache miss
     * @return cached or freshly loaded value
     */
    @SuppressWarnings("unchecked")
    static <T> T remember(Class<?> modelClass, String key, Supplier<T> loader) {
        T cached = Cache.get(key);
        if (cached != null) return cached;
        T value = loader.get();
        if (value != null) {
            Cache.put(key, value, ttl(modelClass));
        }
        return value;
    }

    /**
     * Same as {@link #remember}, but stores the result even when {@code null},
     * for list queries that may legitimately return an empty list.
     *
     * @param modelClass model class, used to resolve TTL
     * @param key        cache key
     * @param loader     called on cache miss
     * @return cached or freshly loaded value
     */
    @SuppressWarnings("unchecked")
    static <T> T rememberList(Class<?> modelClass, String key, Supplier<T> loader) {
        T cached = Cache.get(key);
        if (cached != null) return cached;
        T value = loader.get();
        Cache.put(key, value, ttl(modelClass));
        return value;
    }

    /**
     * Removes the single-row cache entry for the given primary key.
     *
     * @param modelClass model class
     * @param id         primary key value to evict
     */
    static void evictPk(Class<?> modelClass, Object id) {
        Cache.forget(pkKey(modelClass, id));
    }

    /**
     * Removes all cache entries for the given model class.
     * Called automatically on {@code save()} and {@code delete()}.
     *
     * @param modelClass model class to flush
     */
    public static void flush(Class<?> modelClass) {
        Cache.forgetByPrefix(prefix(modelClass));
    }

    /**
     * Returns {@code true} if caching is enabled for the given model class,
     * i.e. it carries {@link Cacheable} and a cache driver is available.
     *
     * @param modelClass model class
     * @return {@code true} if the model is cacheable
     */
    static boolean isEnabled(Class<?> modelClass) {
        return modelClass.isAnnotationPresent(Cacheable.class) && Cache.isAvailable();
    }

    /**
     * Returns the TTL for a model class from its {@link Cacheable} annotation,
     * falling back to 300 seconds if the annotation is absent.
     *
     * @param modelClass model class
     * @return TTL in seconds
     */
    static int ttl(Class<?> modelClass) {
        Cacheable ann = modelClass.getAnnotation(Cacheable.class);
        return ann != null ? ann.ttl() : 300;
    }
}