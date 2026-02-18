package fr.kainovaii.obsidian.database.seeder.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a database seeder.
 * Seeder classes must have a static seed() method.
 * Executed after database initialization, in priority order.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Seeder
{
    /**
     * Execution priority. Lower values run first.
     * Default: 100
     */
    int priority() default 100;
}