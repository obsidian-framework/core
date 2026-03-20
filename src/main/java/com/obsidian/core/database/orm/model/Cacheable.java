package com.obsidian.core.database.orm.model;

import java.lang.annotation.*;

/**
 * Enables query result caching for a Model class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable
{
    /**
     * Cache TTL in seconds. Defaults to 300 (5 minutes).
     */
    int ttl() default 300;
}