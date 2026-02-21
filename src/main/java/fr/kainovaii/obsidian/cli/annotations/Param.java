package fr.kainovaii.obsidian.cli.annotations;

import java.lang.annotation.*;

/**
 * Declares a positional argument on a field.
 *
 * <pre>
 * {@literal @}CliParam(index = 0, name = "name", description = "Controller name")
 * private String name;
 *
 * {@literal @}CliParam(index = 1, name = "fields", description = "Field definitions", variadic = true)
 * private String[] fields;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {
    /** Zero-based position of this argument. */
    int index();
    /** Argument name shown in --help usage line. */
    String name();
    /** Short description shown in --help. */
    String description() default "";
    /** If true, consumes all remaining positional arguments into a String[]. */
    boolean variadic() default false;
    /** If true, the command fails when this argument is missing. */
    boolean required() default true;
}
