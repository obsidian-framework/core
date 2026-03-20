package com.obsidian.core.database.orm.model;

import com.obsidian.core.cache.Cache;

import java.util.List;
import java.util.function.Supplier;

/**
 * ORM-level cache manager.
 */
public final class ModelCache {

    private ModelCache() {}

    // ─── KEY BUILDERS ────────────────────────────────────────

    /**
     * Cache key for a single row by primary key value.
     */
    static String pkKey(Class<?> modelClass, Object id) {
        return "orm:" + modelClass.getSimpleName() + ":pk:" + id;
    }

    /**
     * Cache key for a query result (SQL + bindings fingerprint).
     */
    static String queryKey(Class<?> modelClass, String sql, List<Object> bindings) {
        int hash = 31 * sql.hashCode() + bindings.hashCode();
        return "orm:" + modelClass.getSimpleName() + ":q:" + Integer.toHexString(hash);
    }

    /**
     * Prefix covering all cache entries for a model class.
     */
    static String prefix(Class<?> modelClass) {
        return "orm:" + modelClass.getSimpleName() + ":";
    }

    // ─── READ ────────────────────────────────────────────────

    /**
     * Returns the cached value for {@code key} if present,
     * otherwise computes via {@code loader}, stores it, and returns it.
     *
     * @param modelClass model class (used only to resolve TTL)
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
     * Same as {@link #remember} but stores the result even when {@code null}
     * (used for list queries that may legitimately return an empty list).
     */
    @SuppressWarnings("unchecked")
    static <T> T rememberList(Class<?> modelClass, String key, Supplier<T> loader) {
        T cached = Cache.get(key);
        if (cached != null) return cached;
        T value = loader.get();
        Cache.put(key, value, ttl(modelClass));
        return value;
    }

    // ─── INVALIDATION ────────────────────────────────────────

    /**
     * Removes the single-row cache entry for the given primary key.
     */
    static void evictPk(Class<?> modelClass, Object id) {
        Cache.forget(pkKey(modelClass, id));
    }

    /**
     * Removes all cache entries for the given model class.
     *
     * <p>Called automatically on {@code save()} and {@code delete()}.
     * Can also be called manually when external writes bypass the ORM.</p>
     *
     * <pre>
     *   ModelCache.flush(Kit.class);
     * </pre>
     */
    public static void flush(Class<?> modelClass) {
        Cache.forgetByPrefix(prefix(modelClass));
    }

    // ─── HELPERS ─────────────────────────────────────────────

    /**
     * Returns {@code true} if caching is enabled for the given model class
     * (i.e. the class carries {@link Cacheable}) and a cache driver is available.
     */
    static boolean isEnabled(Class<?> modelClass) {
        return modelClass.isAnnotationPresent(Cacheable.class) && Cache.isAvailable();
    }

    /**
     * Resolves the TTL for a model class from its {@link Cacheable} annotation.
     * Falls back to 300 seconds if the annotation is absent.
     */
    static int ttl(Class<?> modelClass) {
        Cacheable ann = modelClass.getAnnotation(Cacheable.class);
        return ann != null ? ann.ttl() : 300;
    }
}