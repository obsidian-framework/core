package com.obsidian.core.cli;

import java.util.Scanner;
import static com.obsidian.core.cli.AnsiColors.*;

/**
 * Terminal UI components for the Obsidian CLI.
 * Provides a bordered table, an inline progress bar, an animated spinner and interactive prompts.
 * All components degrade gracefully when ANSI is not supported.
 */
public class CliComponents
{
    // =========================================================
    // TABLE
    // =========================================================

    /**
     * A bordered Unicode table with an optional header row.
     * Headers are printed in bold. Column widths are computed automatically.
     */
    public static class Table
    {
        private final String[] headers;
        private final java.util.List<String[]> rows = new java.util.ArrayList<>();

        /**
         * Creates a table with the given column headers.
         *
         * @param headers column header labels
         */
        public Table(String... headers) {
            this.headers = headers;
        }

        /**
         * Adds a data row to the table.
         *
         * @param cells cell values, should match the number of headers
         * @return this table, for chaining
         */
        public Table addRow(String... cells) {
            rows.add(cells);
            return this;
        }

        /**
         * Renders and prints the table to stdout.
         */
        public void print()
        {
            int cols = headers.length;
            int[] widths = new int[cols];

            for (int c = 0; c < cols; c++) widths[c] = headers[c].length();
            for (String[] row : rows) {
                for (int c = 0; c < Math.min(cols, row.length); c++) {
                    widths[c] = Math.max(widths[c], row[c].length());
                }
            }

            System.out.println(borderTop(widths));
            System.out.println(formatRow(headers, widths, true));
            System.out.println(borderMid(widths));
            for (String[] row : rows) System.out.println(formatRow(row, widths, false));
            System.out.println(borderBot(widths));
        }

        private String formatRow(String[] cells, int[] widths, boolean isHeader)
        {
            StringBuilder sb = new StringBuilder("│");
            for (int c = 0; c < widths.length; c++) {
                String cell   = c < cells.length ? cells[c] : "";
                String padded = " " + pad(cell, widths[c]) + " ";
                sb.append(isHeader ? colorize(padded, BOLD) : padded).append("│");
            }
            return sb.toString();
        }

        private String borderTop(int[] widths) { return border(widths, "┌", "┬", "┐", "─"); }
        private String borderMid(int[] widths) { return border(widths, "├", "┼", "┤", "─"); }
        private String borderBot(int[] widths) { return border(widths, "└", "┴", "┘", "─"); }

        private String border(int[] widths, String l, String m, String r, String h)
        {
            StringBuilder sb = new StringBuilder(l);
            for (int c = 0; c < widths.length; c++) {
                sb.append(h.repeat(widths[c] + 2));
                sb.append(c < widths.length - 1 ? m : r);
            }
            return sb.toString();
        }

        private String pad(String s, int len) {
            return s + " ".repeat(Math.max(0, len - s.length()));
        }
    }

    // =========================================================
    // PROGRESS BAR
    // =========================================================

    /**
     * An inline progress bar that updates in place using carriage return.
     * Displays the current step, percentage and a filled/empty bar.
     */
    public static class ProgressBar
    {
        private static final int    BAR_WIDTH = 30;
        private static final String FILLED    = "█";
        private static final String EMPTY     = "░";

        private final int    total;
        private final String label;

        /**
         * Creates a progress bar.
         *
         * @param total the total number of steps
         * @param label the label displayed before the bar
         */
        public ProgressBar(int total, String label) {
            this.total = total;
            this.label = label;
        }

        /**
         * Updates the progress bar to the given step.
         *
         * @param current current step, between 0 and total
         */
        public void update(int current)
        {
            int    pct    = total == 0 ? 100 : (int) ((current / (double) total) * 100);
            int    filled = (int) ((current / (double) total) * BAR_WIDTH);
            int    empty  = BAR_WIDTH - filled;
            String bar    = FILLED.repeat(filled) + EMPTY.repeat(empty);
            String line   = " " + label + " [" + colorize(bar, GREEN) + "] " + pct + "% (" + current + "/" + total + ")";
            System.out.print("\r" + line);
        }

        /**
         * Completes the progress bar at 100% and prints a newline.
         * Should be called after the last update.
         */
        public void finish() {
            update(total);
            System.out.println();
        }
    }

    // =========================================================
    // SPINNER
    // =========================================================

    /**
     * An animated spinner that runs on a background thread.
     * Call stop() to terminate it.
     */
    public static class Spinner
    {
        private static final String[] FRAMES = { "|", "/", "-", "\\" };

        private final String         label;
        private       Thread         thread;
        private volatile boolean     running = false;

        /**
         * Creates a spinner with the given label.
         *
         * @param label the text displayed next to the spinner
         */
        public Spinner(String label) {
            this.label = label;
        }

        /**
         * Starts the spinner on a background daemon thread.
         */
        public void start()
        {
            running = true;
            thread  = new Thread(() -> {
                int i = 0;
                while (running) {
                    String frame = colorize(FRAMES[i % FRAMES.length], CYAN, BOLD);
                    System.out.print("\r" + frame + " " + label);
                    i++;
                    try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        /**
         * Stops the spinner and prints a final message.
         *
         * @param finalMessage the message to display after stopping
         */
        public void stop(String finalMessage)
        {
            running = false;
            try { thread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.print("\r" + " ".repeat(label.length() + 4) + "\r");
            System.out.println(finalMessage);
        }
    }

    // =========================================================
    // PROMPTS
    // =========================================================

    /**
     * Interactive prompts for user input.
     * Supports free text, yes/no confirmation and single-choice selection.
     */
    public static class Prompt
    {
        private static final Scanner SCANNER = new Scanner(System.in);

        private static final String MARK    = colorize("?", YELLOW, BOLD);
        private static final String BAR     = colorize("|", DIM);
        private static final String BAR_END = colorize("└", DIM);
        private static final String ARROW   = colorize(">", CYAN, BOLD);
        private static final String DOT_ON  = colorize("*", CYAN, BOLD);
        private static final String DOT_OFF = colorize("-", DIM);

        /**
         * Prompts the user for free text input.
         * Returns the default value if the user presses Enter without typing.
         *
         * @param question     the question to display
         * @param defaultValue the value returned when input is empty
         * @return the user input, or defaultValue if empty
         */
        public static String text(String question, String defaultValue)
        {
            String def = defaultValue != null && !defaultValue.isEmpty()
                    ? " " + colorize("(" + defaultValue + ")", DIM)
                    : "";

            System.out.println(MARK + "  " + bold(question) + def);
            System.out.print(BAR + "  ");
            String input = SCANNER.nextLine().trim();
            String value = (input.isEmpty() && defaultValue != null) ? defaultValue : input;
            if (input.isEmpty()) {
                System.out.println(BAR_END);
            } else {
                System.out.println(BAR_END + "  " + colorize(value, DIM));
            }
            System.out.println();
            return value;
        }

        /**
         * Prompts the user for free text input with no default.
         *
         * @param question the question to display
         * @return the user input
         */
        public static String text(String question) {
            return text(question, null);
        }

        /**
         * Prompts the user for a yes/no confirmation.
         *
         * @param question      the question to display
         * @param defaultAnswer the default answer when the user presses Enter
         * @return true if the user answered yes
         */
        public static boolean confirm(String question, boolean defaultAnswer)
        {
            String yes = colorize("Yes", defaultAnswer ? CYAN : DIM);
            String no  = colorize("No",  defaultAnswer ? DIM  : CYAN);
            String hint = yes + colorize(" / ", DIM) + no;

            System.out.println(MARK + "  " + bold(question));
            System.out.print(BAR + "  " + hint + "  " + ARROW + " ");

            String input = SCANNER.nextLine().trim().toLowerCase();
            boolean value = input.isEmpty() ? defaultAnswer : input.equals("y") || input.equals("yes");

            System.out.println(BAR_END + "  " + colorize(value ? "Yes" : "No", DIM));
            System.out.println();
            return value;
        }

        /**
         * Prompts the user to select one option from a list.
         * The user can type the number or the option name.
         *
         * @param question the question to display
         * @param options  the list of choices
         * @return the selected option
         */
        public static String select(String question, String... options)
        {
            System.out.println(MARK + "  " + bold(question));
            for (int i = 0; i < options.length; i++) {
                System.out.println(BAR + "  " + colorize((i + 1) + ")", DIM) + " " + options[i]);
            }
            System.out.print(BAR_END + "  " + ARROW + " ");

            String selected = null;
            while (selected == null) {
                String input = SCANNER.nextLine().trim();
                try {
                    int idx = Integer.parseInt(input) - 1;
                    if (idx >= 0 && idx < options.length) selected = options[idx];
                } catch (NumberFormatException ignored) {}
                if (selected == null) {
                    for (String opt : options) {
                        if (opt.equalsIgnoreCase(input)) { selected = opt; break; }
                    }
                }
                if (selected == null) {
                    System.out.print("  " + colorize("Invalid, try again: ", RED) + ARROW + " ");
                }
            }

            System.out.println(colorize("  " + selected, DIM));
            System.out.println();
            return selected;
        }
    }
}