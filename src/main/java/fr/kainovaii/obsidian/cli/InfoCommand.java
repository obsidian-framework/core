package fr.kainovaii.obsidian.cli;

import fr.kainovaii.obsidian.cli.annotations.Command;

import static fr.kainovaii.obsidian.cli.AnsiColors.*;
import static fr.kainovaii.obsidian.cli.Printer.*;

/**
 * Built-in command that displays framework and environment information.
 */
@Command(name = "info", description = "Display framework and environment information")
public class InfoCommand implements Runnable
{
    @Override
    public void run()
    {
        print(bold("Obsidian") + " " + cyan(Cli.VERSION));
        print();
        print(bold("Environment"));
        print("  Java        " + System.getProperty("java.version"));
        print("  OS          " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        print("  Working dir " + System.getProperty("user.dir"));
        print();
    }
}