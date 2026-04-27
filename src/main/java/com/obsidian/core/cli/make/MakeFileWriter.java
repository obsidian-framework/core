package com.obsidian.core.cli.make;

import com.obsidian.core.cli.Printer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Shared utility for all make:* commands.
 */
public final class MakeFileWriter
{
    private MakeFileWriter() {}

    /**
     * Resolves the project root by walking up from user.dir until pom.xml is found.
     */
    public static Path projectRoot()
    {
        Path dir = Path.of(System.getProperty("user.dir"));
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml"))) return dir;
            dir = dir.getParent();
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * Returns src/main/java relative to the project root.
     */
    public static Path sourceRoot()
    {
        return projectRoot().resolve("src/main/java");
    }

    /**
     * Detects the application base package by finding the class with main().
     */
    public static String detectBasePackage()
    {
        Path src = sourceRoot();
        try {
            var found = Files.walk(src)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try { return Files.readString(p).contains("public static void main"); }
                        catch (Exception e) { return false; }
                    })
                    .findFirst();

            if (found.isPresent()) {
                String rel = src.relativize(found.get()).toString()
                        .replace(File.separatorChar, '.')
                        .replace(".java", "");
                int dot = rel.lastIndexOf('.');
                return dot > 0 ? rel.substring(0, dot) : rel;
            }
        } catch (Exception ignored) {}
        return "app";
    }

    /**
     * Writes a file, creating parent directories as needed.
     * Aborts if the file already exists.
     */
    public static boolean write(Path target, String content)
    {
        if (Files.exists(target)) {
            Printer.warning("File already exists: " + target);
            return false;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return true;
        } catch (IOException e) {
            Printer.error("Could not write file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Prints the creation success line with relative path.
     */
    public static void printCreated(String type, Path target)
    {
        Path cwd = Path.of(System.getProperty("user.dir"));
        String rel = cwd.relativize(target).toString().replace(File.separatorChar, '/');
        Printer.ok(type + " created successfully.");
        Printer.print("  \u001B[90m→\u001B[0m " + rel);
        Printer.print();
    }

    /**
     * Converts "BlogPost" → "blog_posts".
     */
    public static String toTableName(String className)
    {
        String snake = className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return snake.endsWith("s") ? snake : snake + "s";
    }

    /**
     * Returns a timestamp prefix for migration file names: "20260425_143012".
     */
    public static String migrationTimestamp()
    {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%04d%02d%02d_%02d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
    }

    /**
     * Converts dot-separated package to directory path.
     */
    public static Path pkgToPath(String pkg)
    {
        return Path.of(pkg.replace('.', File.separatorChar));
    }

    /**
     * Ensures the class name starts uppercase and ends with the given suffix.
     */
    public static String normalize(String input, String suffix)
    {
        String n = Character.toUpperCase(input.charAt(0)) + input.substring(1);
        return n.endsWith(suffix) ? n : n + suffix;
    }

    /**
     * Converts snake_case to PascalCase. "create_users_table" → "CreateUsersTable"
     */
    public static String toPascalCase(String snake)
    {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (!part.isEmpty())
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
