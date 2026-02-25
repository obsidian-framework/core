package fr.kainovaii.obsidian.cli;
import static fr.kainovaii.obsidian.cli.AnsiColors.*;

/**
 * Utility class for printing to the terminal.
 */
public class Printer {

    public static void print(String text)   { System.out.println(text); }
    public static void print()              { System.out.println(); }
    public static void err(String text)     { System.err.println(error(text)); }

}