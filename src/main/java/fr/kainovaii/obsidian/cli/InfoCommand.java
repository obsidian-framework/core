package fr.kainovaii.obsidian.cli;

import fr.kainovaii.obsidian.cli.annotations.Command;

/**
 * Built-in command that displays framework and environment information.
 */
@Command(name = "info", description = "Display framework and environment information")
public class InfoCommand implements Runnable {

    @Override
    public void run() {
        String bold  = "\u001B[1m";
        String cyan  = "\u001B[36m";
        String reset = "\u001B[0m";

        System.out.println(bold + "Obsidian" + reset + " " + cyan + Cli.VERSION + reset);
        System.out.println();
        System.out.println(bold + "Environment" + reset);
        System.out.println("  Java        " + System.getProperty("java.version"));
        System.out.println("  OS          " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        System.out.println("  Working dir " + System.getProperty("user.dir"));
    }
}
