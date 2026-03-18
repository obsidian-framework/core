package com.obsidian.core.cli;

import static com.obsidian.core.cli.AnsiColors.*;

/**
 * Utility class for printing to the terminal.
 * Provides basic print methods and Symfony-style styled message blocks.
 * Blocks degrade to plain text when ANSI is not supported.
 */
public class Printer
{
    /** Prints a line of text to stdout. */
    public static void print(String text) { System.out.println(text); }

    /** Prints a blank line to stdout. */
    public static void print()            { System.out.println(); }

    /** Prints a line of text to stderr. */
    public static void err(String text)   { System.err.println(text); }

    /** Prints a green success block. */
    public static void ok(String message)      { block("OK",      message, BG_GREEN,      WHITE); }

    /** Prints a red error block. */
    public static void error(String message)   { block("ERROR",   message, BG_RED,        WHITE); }

    /** Prints a yellow warning block. */
    public static void warning(String message) { block("WARNING", message, BG_YELLOW,     BLACK); }

    /** Prints a cyan note block. */
    public static void note(String message)    { block("NOTE",    message, BG_CYAN,       WHITE); }

    /** Prints a blue info block. */
    public static void info(String message)    { block("INFO",    message, BG_BLUE,       WHITE); }

    /** Prints a bright red caution block. */
    public static void caution(String message) { block("CAUTION", message, BG_RED_BRIGHT, WHITE); }

    private static void block(String label, String message, String bg, String fg)
    {
        if (!ANSI) {
            System.out.println(" [" + label + "] " + message);
            return;
        }

        String content = " [" + label + "] " + message + " ";
        String padding = " ".repeat(content.length());

        System.out.println(colorize(padding, bg, fg, BOLD));
        System.out.println(colorize(content, bg, fg, BOLD));
        System.out.println(colorize(padding, bg, fg, BOLD));
    }
}