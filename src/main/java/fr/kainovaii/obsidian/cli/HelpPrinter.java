package fr.kainovaii.obsidian.cli;

import fr.kainovaii.obsidian.cli.annotations.Command;
import fr.kainovaii.obsidian.cli.annotations.Option;
import fr.kainovaii.obsidian.cli.annotations.Param;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Generates --help output for commands from their annotations.
 * Uses built-in ANSI escape codes — no external dependencies.
 */
public class HelpPrinter
{
    public static void printGlobal(String version, List<Class<?>> commands)
    {
        System.out.println(bold("Obsidian") + " " + cyan(version));
        System.out.println();
        System.out.println(bold("Usage:"));
        System.out.println("  obsidian <command> [options]");
        System.out.println();
        System.out.println(bold("Commands:"));

        // Deduplicate (aliases point to the same class)
        List<Class<?>> unique = commands.stream().distinct().toList();

        int maxLen = unique.stream()
            .filter(c -> c.isAnnotationPresent(Command.class))
            .mapToInt(c -> c.getAnnotation(Command.class).name().length())
            .max().orElse(10);

        for (Class<?> cls : unique) {
            if (!cls.isAnnotationPresent(Command.class)) continue;
            Command meta = cls.getAnnotation(Command.class);
            String aliases  = meta.aliases().length > 0
                ? dim(" (" + String.join(", ", meta.aliases()) + ")")
                : "";
            System.out.printf("  %s%s  %s%n", green(pad(meta.name(), maxLen)), aliases, meta.description());
        }

        System.out.println();
        System.out.println("Run " + bold("obsidian <command> --help") + " for command-specific help.");
    }

    public static void printCommand(Class<?> cls)
    {
        Command meta = cls.getAnnotation(Command.class);
        if (meta == null) return;

        System.out.println(bold(meta.name()) + "  " + meta.description());
        System.out.println();

        List<Field> params  = new ArrayList<>();
        List<Field> options = new ArrayList<>();
        for (Field f : cls.getDeclaredFields()) {
            if (f.isAnnotationPresent(Param.class))  params.add(f);
            if (f.isAnnotationPresent(Option.class)) options.add(f);
        }
        params.sort(Comparator.comparingInt(f -> f.getAnnotation(Param.class).index()));

        // Usage line
        StringBuilder usage = new StringBuilder("  obsidian ").append(meta.name());
        for (Field f : params) {
            Param p = f.getAnnotation(Param.class);
            usage.append(p.required() ? " <" + p.name() + ">" : " [" + p.name() + "]");
            if (p.variadic()) usage.append("...");
        }
        if (!options.isEmpty()) usage.append(" [options]");

        System.out.println(bold("Usage:"));
        System.out.println(usage);
        System.out.println();

        if (!params.isEmpty()) {
            System.out.println(bold("Arguments:"));
            for (Field f : params) {
                Param p = f.getAnnotation(Param.class);
                System.out.printf("  %s  %s%n", cyan(pad("<" + p.name() + ">", 20)), p.description());
            }
            System.out.println();
        }

        if (!options.isEmpty()) {
            System.out.println(bold("Options:"));
            for (Field f : options) {
                Option o = f.getAnnotation(Option.class);
                String def = o.defaultValue().isEmpty() ? "" : dim(" [default: " + o.defaultValue() + "]");
                String req = o.required() ? red(" (required)") : "";
                System.out.printf("  %s  %s%s%s%n", cyan(pad(o.name(), 20)), o.description(), def, req);
            }
            System.out.println();
        }
    }

    private static final boolean ANSI = System.console() != null
        || System.getenv("FORCE_COLOR") != null;

    private static String bold(String s)  { return ANSI ? "\u001B[1m"  + s + "\u001B[0m" : s; }
    private static String dim(String s)   { return ANSI ? "\u001B[2m"  + s + "\u001B[0m" : s; }
    private static String cyan(String s)  { return ANSI ? "\u001B[36m" + s + "\u001B[0m" : s; }
    private static String green(String s) { return ANSI ? "\u001B[32m" + s + "\u001B[0m" : s; }
    private static String red(String s)   { return ANSI ? "\u001B[31m" + s + "\u001B[0m" : s; }

    private static String pad(String s, int len) {
        return s + " ".repeat(Math.max(0, len - s.length()));
    }
}
