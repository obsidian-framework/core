package fr.kainovaii.obsidian.cli;

import fr.kainovaii.obsidian.cli.annotations.Option;
import fr.kainovaii.obsidian.cli.annotations.Param;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Parses a raw args array and injects values into a command instance
 * via reflection, based on {@link Option} and {@link Param}.
 */
public class ArgParser
{
    /**
     * Injects parsed args into {@code instance}.
     *
     * @param instance The command instance to populate
     * @param args     Raw args after the command name
     * @throws CliException If a required arg is missing or a value is invalid
     */
    public static void inject(Object instance, String[] args) throws CliException
    {
        Class<?> cls = instance.getClass();

        Map<String, Field> options = new HashMap<>();
        Map<Integer, Field> params = new TreeMap<>();
        Field variadicField = null;

        // Index annotated fields
        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            if (f.isAnnotationPresent(Option.class)) {
                options.put(f.getAnnotation(Option.class).name(), f);
            }
            if (f.isAnnotationPresent(Param.class)) {
                Param p = f.getAnnotation(Param.class);
                if (p.variadic()) variadicField = f;
                else              params.put(p.index(), f);
            }
        }

        applyDefaults(instance, cls);

        // Parse options and collect positional args
        List<String> positional = new ArrayList<>();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                Field f = options.get(arg);
                if (f == null) throw new CliException("Unknown option: " + arg);
                Option opt = f.getAnnotation(Option.class);
                if (opt.flag()) {
                    set(instance, f, true);
                } else {
                    if (i + 1 >= args.length) throw new CliException("Option " + arg + " requires a value.");
                    set(instance, f, args[++i]);
                }
            } else {
                positional.add(arg);
            }
            i++;
        }

        // Inject positional args
        for (Map.Entry<Integer, Field> e : params.entrySet()) {
            int idx = e.getKey();
            Field f = e.getValue();
            Param p = f.getAnnotation(Param.class);
            if (idx < positional.size()) {
                set(instance, f, positional.get(idx));
            } else if (p.required()) {
                throw new CliException("Missing required argument: <" + p.name() + ">");
            }
        }

        // Inject variadic field
        if (variadicField != null) {
            List<String> rest = positional.subList(Math.min(params.size(), positional.size()), positional.size());
            set(instance, variadicField, rest.toArray(new String[0]));
        }

        // Check required options
        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            if (!f.isAnnotationPresent(Option.class)) continue;
            Option opt = f.getAnnotation(Option.class);
            if (!opt.required()) continue;
            try {
                if (f.get(instance) == null) throw new CliException("Missing required option: " + opt.name());
            } catch (IllegalAccessException ignored) {}
        }
    }

    private static void set(Object instance, Field f, Object raw) throws CliException
    {
        try {
            f.set(instance, coerce(raw, f.getType()));
        } catch (IllegalAccessException e) {
            throw new CliException("Cannot set field: " + f.getName());
        }
    }

    private static Object coerce(Object raw, Class<?> type) throws CliException
    {
        if (type.isInstance(raw)) return raw;
        String s = raw.toString();
        try {
            if (type == int.class     || type == Integer.class) return Integer.parseInt(s);
            if (type == long.class    || type == Long.class)    return Long.parseLong(s);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(s);
            if (type == String.class)                           return s;
            if (type == String[].class && raw instanceof String[] arr) return arr;
        } catch (NumberFormatException e) {
            throw new CliException("Invalid value '" + s + "' for type " + type.getSimpleName());
        }
        return raw;
    }

    private static void applyDefaults(Object instance, Class<?> cls)
    {
        for (Field f : cls.getDeclaredFields()) {
            f.setAccessible(true);
            if (!f.isAnnotationPresent(Option.class)) continue;
            String def = f.getAnnotation(Option.class).defaultValue();
            if (def.isEmpty()) continue;
            try {
                Object current = f.get(instance);
                if (current == null || isPrimitivZero(current)) {
                    f.set(instance, coerce(def, f.getType()));
                }
            } catch (Exception ignored) {}
        }
    }

    private static boolean isPrimitivZero(Object v)
    {
        if (v instanceof Integer n)  return n == 0;
        if (v instanceof Long n)     return n == 0L;
        if (v instanceof Boolean b)  return !b;
        return false;
    }
}
