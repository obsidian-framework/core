package fr.kainovaii.obsidian.cli;

/**
 * Thrown when a CLI argument is invalid or missing.
 */
public class CliException extends Exception {
    public CliException(String message) {
        super(message);
    }
}
