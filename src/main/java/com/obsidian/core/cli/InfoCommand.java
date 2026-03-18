package com.obsidian.core.cli;

import com.obsidian.core.cli.annotations.Command;

import static com.obsidian.core.cli.AnsiColors.*;
import static com.obsidian.core.cli.Printer.*;

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