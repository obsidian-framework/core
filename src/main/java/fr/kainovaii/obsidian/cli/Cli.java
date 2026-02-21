package fr.kainovaii.obsidian.cli;

import fr.kainovaii.obsidian.cli.annotations.Command;

import java.util.*;

/**
 * Entry point for the Obsidian CLI.
 * Zero external dependencies.
 *
 * Called from {@link fr.kainovaii.obsidian.core.Obsidian#startCli()}.
 *
 * The application's main class must pass args to the framework:
 *
 */
public class Cli
{
    static final String VERSION = "1.1.0";

    /** Registered commands: built-in + auto-discovered. */
    private final Map<String, Class<?>> registry = new LinkedHashMap<>();

    private Cli()
    {
        // Built-in commands
        register(InfoCommand.class);

        // Auto-discover @CliCommand classes from the classpath
        for (Class<?> cls : CommandDiscovery.discover())
        {
            if (!registry.containsValue(cls)) register(cls); // skip already registered
        }
    }

    private void register(Class<?> cls)
    {
        Command meta = cls.getAnnotation(Command.class);
        if (meta == null) return;
        registry.put(meta.name(), cls);
        for (String alias : meta.aliases()) registry.put(alias, cls);
    }

    /**
     * Handles CLI args passed from the main method.
     * Executes the matching command and exits, or returns silently if no command matched
     * so the server can start normally.
     *
     * @param args The args from main(String[] args)
     */
    public static void handle(String[] args)
    {
        if (args == null || args.length == 0) return;

        Cli cli = new Cli();

        switch (args[0]) {
            case "--help", "-h" -> { cli.printHelp(); System.exit(0); }
            case "--version", "-v" -> { System.out.println("Obsidian " + VERSION); System.exit(0); }
        }

        if (!cli.registry.containsKey(args[0])) return; // not a CLI command → let server start

        cli.execute(args);
        System.exit(0);
    }

    private void execute(String[] args)
    {
        String name      = args[0];
        String[] cmdArgs = Arrays.copyOfRange(args, 1, args.length);
        Class<?> cls     = registry.get(name);

        // --help on a specific command
        if (cmdArgs.length > 0 && (cmdArgs[0].equals("--help") || cmdArgs[0].equals("-h"))) {
            HelpPrinter.printCommand(cls);
            return;
        }

        try {
            Runnable instance = (Runnable) cls.getDeclaredConstructor().newInstance();
            ArgParser.inject(instance, cmdArgs);
            instance.run();
        } catch (CliException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run 'obsidian " + name + " --help' for usage.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Failed to run command '" + name + "': " + e.getMessage());
            System.exit(1);
        }
    }

    private void printHelp() {
        HelpPrinter.printGlobal(VERSION, new ArrayList<>(registry.values()));
    }
}
