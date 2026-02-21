package fr.kainovaii.obsidian.cli.annotations;

import java.lang.annotation.*;

/**
 * Declares a CLI option on a field.
 *
 * <pre>
 * {@literal @}CliOption(name = "--port", description = "Port to listen on", defaultValue = "8080")
 * private int port;
 *
 * {@literal @}CliOption(name = "--force", description = "Skip confirmation", flag = true)
 * private boolean force;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Option {
    /** Option name, e.g. "--port". */
    String name();
    /** Short description shown in --help. */
    String description() default "";
    /** Default value applied when the option is not provided. */
    String defaultValue() default "";
    /** If true, the option is a boolean flag and expects no value. */
    boolean flag() default false;
    /** If true, the command fails when this option is missing. */
    boolean required() default false;
}
