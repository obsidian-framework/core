package fr.kainovaii.obsidian.cli.annotations;

import java.lang.annotation.*;

/**
 * Marks a class as an Obsidian CLI command.
 *
 * Any class annotated with {@code @CliCommand} in the classpath is automatically
 * discovered and registered in the CLI at startup.
 *
 * <pre>
 * {@literal @}CliCommand(name = "deploy", description = "Deploy to production")
 * public class DeployCommand implements Runnable {
 *     public void run() { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {
    /** The command name, e.g. "deploy". */
    String name();
    /** Short description shown in --help. */
    String description() default "";
    /** Optional aliases, e.g. {"d"}. */
    String[] aliases() default {};
}
