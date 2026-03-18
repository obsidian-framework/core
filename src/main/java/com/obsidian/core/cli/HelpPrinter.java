package com.obsidian.core.cli;

import com.obsidian.core.cli.annotations.Command;
import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;

import java.lang.reflect.Field;
import java.util.*;

import static com.obsidian.core.cli.AnsiColors.*;
import static com.obsidian.core.cli.Printer.*;

/**
 * Generates --help output for commands from their annotations.
 */
public class HelpPrinter
{
    /**
     * Prints the global help listing all available commands.
     *
     * @param version  the CLI version string displayed in the header
     * @param commands list of command classes (duplicates are deduplicated)
     */
    public static void printGlobal(String version, List<Class<?>> commands)
    {
        print(bold("Obsidian") + " " + cyan(version));
        print();
        print(bold("Usage:"));
        print("  obsidian <command> [options]");
        print();
        print(bold("Commands:"));

        List<Class<?>> unique = commands.stream().distinct().toList();

        int maxLen = unique.stream()
                .filter(c -> c.isAnnotationPresent(Command.class))
                .mapToInt(c -> c.getAnnotation(Command.class).name().length())
                .max().orElse(10);

        for (Class<?> cls : unique) {
            if (!cls.isAnnotationPresent(Command.class)) continue;
            Command meta = cls.getAnnotation(Command.class);
            String aliases = meta.aliases().length > 0
                    ? dim(" (" + String.join(", ", meta.aliases()) + ")")
                    : "";
            print("  " + green(pad(meta.name(), maxLen)) + aliases + "  " + meta.description());
        }

        print();
        print("Run " + bold("obsidian <command> --help") + " for command-specific help.");
    }

    /**
     * Prints the detailed help for a single command.
     *
     * @param cls the command class annotated with {@link Command}
     */
    public static void printCommand(Class<?> cls)
    {
        Command meta = cls.getAnnotation(Command.class);
        if (meta == null) return;

        print(bold(meta.name()) + "  " + meta.description());
        print();

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

        print(bold("Usage:"));
        print(usage.toString());
        print();

        if (!params.isEmpty()) {
            print(bold("Arguments:"));
            for (Field f : params) {
                Param p = f.getAnnotation(Param.class);
                print("  " + cyan(pad("<" + p.name() + ">", 20)) + "  " + p.description());
            }
            print();
        }

        if (!options.isEmpty()) {
            print(bold("Options:"));
            for (Field f : options) {
                Option o = f.getAnnotation(Option.class);
                String def = o.defaultValue().isEmpty() ? "" : dim(" [default: " + o.defaultValue() + "]");
                String req = o.required() ? red(" (required)") : "";
                print("  " + cyan(pad(o.name(), 20)) + "  " + o.description() + def + req);
            }
            print();
        }
    }

    /**
     * Pads a string with trailing spaces up to the given length.
     *
     * @param s   the string to pad
     * @param len the target length
     * @return the padded string
     */
    private static String pad(String s, int len) {
        return s + " ".repeat(Math.max(0, len - s.length()));
    }
}