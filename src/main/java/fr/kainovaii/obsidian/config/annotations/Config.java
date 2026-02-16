package fr.kainovaii.obsidian.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as application configuration.
 * Configuration classes are auto-discovered and their configure() method is called at startup.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config
{
    /**
     * Configuration priority (lower values execute first).
     * Default is 100.
     *
     * @return Priority value
     */
    int priority() default 100;
}