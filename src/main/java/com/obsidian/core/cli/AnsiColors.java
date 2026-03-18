package com.obsidian.core.cli;

/**
 * ANSI Helper - Colors and styles for the terminal
 */
public class AnsiColors
{
    // ─── ANSI support detection ───────────────────────────────
    public static final boolean ANSI = System.console() != null || System.getenv("FORCE_COLOR") != null;

    // ─── Reset ───────────────────────────────────────────────
    public static final String RESET       = "\u001B[0m";

    // ─── Styles ──────────────────────────────────────────────
    public static final String BOLD        = "\u001B[1m";
    public static final String DIM         = "\u001B[2m";
    public static final String ITALIC      = "\u001B[3m";
    public static final String UNDERLINE   = "\u001B[4m";
    public static final String BLINK       = "\u001B[5m";
    public static final String REVERSED    = "\u001B[7m";
    public static final String STRIKETHROUGH = "\u001B[9m";

    // ─── Text colors ─────────────────────────────────────────
    public static final String BLACK       = "\u001B[30m";
    public static final String RED         = "\u001B[31m";
    public static final String GREEN       = "\u001B[32m";
    public static final String YELLOW      = "\u001B[33m";
    public static final String BLUE        = "\u001B[34m";
    public static final String MAGENTA     = "\u001B[35m";
    public static final String CYAN        = "\u001B[36m";
    public static final String WHITE       = "\u001B[37m";

    // ─── Bright text colors ───────────────────────────────────
    public static final String BLACK_BRIGHT   = "\u001B[90m";
    public static final String RED_BRIGHT     = "\u001B[91m";
    public static final String GREEN_BRIGHT   = "\u001B[92m";
    public static final String YELLOW_BRIGHT  = "\u001B[93m";
    public static final String BLUE_BRIGHT    = "\u001B[94m";
    public static final String MAGENTA_BRIGHT = "\u001B[95m";
    public static final String CYAN_BRIGHT    = "\u001B[96m";
    public static final String WHITE_BRIGHT   = "\u001B[97m";

    // ─── Background colors ────────────────────────────────────
    public static final String BG_BLACK     = "\u001B[40m";
    public static final String BG_RED       = "\u001B[41m";
    public static final String BG_GREEN     = "\u001B[42m";
    public static final String BG_YELLOW    = "\u001B[43m";
    public static final String BG_BLUE      = "\u001B[44m";
    public static final String BG_MAGENTA   = "\u001B[45m";
    public static final String BG_CYAN      = "\u001B[46m";
    public static final String BG_WHITE     = "\u001B[47m";

    // ─── Bright background colors ─────────────────────────────
    public static final String BG_BLACK_BRIGHT   = "\u001B[100m";
    public static final String BG_RED_BRIGHT     = "\u001B[101m";
    public static final String BG_GREEN_BRIGHT   = "\u001B[102m";
    public static final String BG_YELLOW_BRIGHT  = "\u001B[103m";
    public static final String BG_BLUE_BRIGHT    = "\u001B[104m";
    public static final String BG_MAGENTA_BRIGHT = "\u001B[105m";
    public static final String BG_CYAN_BRIGHT    = "\u001B[106m";
    public static final String BG_WHITE_BRIGHT   = "\u001B[107m";

    // ─── Utility methods ─────────────────────────────────────

    /** Applies one or more styles to a text and resets automatically */
    public static String colorize(String text, String... styles) {
        if (!ANSI) return text;
        StringBuilder sb = new StringBuilder();
        for (String style : styles) sb.append(style);
        sb.append(text).append(RESET);
        return sb.toString();
    }

    /** Handy shortcuts */
    public static String dim(String text)     { return colorize(text, DIM); }
    public static String red(String text)     { return colorize(text, RED); }
    public static String green(String text)   { return colorize(text, GREEN); }
    public static String yellow(String text)  { return colorize(text, YELLOW); }
    public static String blue(String text)    { return colorize(text, BLUE); }
    public static String cyan(String text)    { return colorize(text, CYAN); }
    public static String magenta(String text) { return colorize(text, MAGENTA); }
    public static String white(String text)   { return colorize(text, WHITE); }
    public static String bold(String text)    { return colorize(text, BOLD); }
    public static String error(String text)   { return colorize(text, BOLD, RED); }
    public static String success(String text) { return colorize(text, BOLD, GREEN); }
    public static String warn(String text)    { return colorize(text, BOLD, YELLOW); }
    public static String info(String text)    { return colorize(text, BOLD, CYAN); }
}