package com.obsidian.core.cli;

import com.obsidian.core.cli.annotations.Option;
import com.obsidian.core.cli.annotations.Param;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArgParserTest
{
    // ──────────────────────────────────────────────
    // Test fixtures
    // ──────────────────────────────────────────────

    static class SimpleCommand {
        @Param(index = 0, name = "name", required = true)
        String name;
    }

    static class OptionalParamCommand {
        @Param(index = 0, name = "name", required = false)
        String name;
    }

    static class OptionCommand {
        @Option(name = "--output")
        String output;
    }

    static class RequiredOptionCommand {
        @Option(name = "--env", required = true)
        String env;
    }

    static class FlagCommand {
        @Option(name = "--verbose", flag = true)
        boolean verbose;
    }

    static class DefaultOptionCommand {
        @Option(name = "--port", defaultValue = "8080")
        int port;
    }

    static class MixedCommand {
        @Param(index = 0, name = "file", required = true)
        String file;

        @Option(name = "--force", flag = true)
        boolean force;

        @Option(name = "--output")
        String output;
    }

    static class IntParamCommand {
        @Option(name = "--count")
        int count;
    }

    static class VariadicCommand {
        @Param(index = 0, name = "target", required = true)
        String target;

        @Param(index = 1, name = "files", variadic = true)
        String[] files;
    }

    // ──────────────────────────────────────────────
    // Positional params
    // ──────────────────────────────────────────────

    @Test
    void positionalParam_injected() throws CliException {
        SimpleCommand cmd = new SimpleCommand();
        ArgParser.inject(cmd, new String[]{"Alice"});

        assertEquals("Alice", cmd.name);
    }

    @Test
    void positionalParam_required_missing_throws() {
        SimpleCommand cmd = new SimpleCommand();

        assertThrows(CliException.class, () -> ArgParser.inject(cmd, new String[]{}));
    }

    @Test
    void positionalParam_optional_missing_staysNull() throws CliException {
        OptionalParamCommand cmd = new OptionalParamCommand();
        ArgParser.inject(cmd, new String[]{});

        assertNull(cmd.name);
    }

    // ──────────────────────────────────────────────
    // Options
    // ──────────────────────────────────────────────

    @Test
    void option_withValue_injected() throws CliException {
        OptionCommand cmd = new OptionCommand();
        ArgParser.inject(cmd, new String[]{"--output", "result.txt"});

        assertEquals("result.txt", cmd.output);
    }

    @Test
    void option_equalsSign_syntax() throws CliException {
        OptionCommand cmd = new OptionCommand();
        ArgParser.inject(cmd, new String[]{"--output=result.txt"});

        assertEquals("result.txt", cmd.output);
    }

    @Test
    void option_required_missing_throws() {
        RequiredOptionCommand cmd = new RequiredOptionCommand();

        assertThrows(CliException.class, () -> ArgParser.inject(cmd, new String[]{}));
    }

    @Test
    void option_unknown_throws() {
        SimpleCommand cmd = new SimpleCommand();

        assertThrows(CliException.class,
                () -> ArgParser.inject(cmd, new String[]{"--unknown", "val"}));
    }

    @Test
    void option_missingValue_throws() {
        OptionCommand cmd = new OptionCommand();

        assertThrows(CliException.class,
                () -> ArgParser.inject(cmd, new String[]{"--output"}));
    }

    // ──────────────────────────────────────────────
    // Flags
    // ──────────────────────────────────────────────

    @Test
    void flag_present_setsTrue() throws CliException {
        FlagCommand cmd = new FlagCommand();
        ArgParser.inject(cmd, new String[]{"--verbose"});

        assertTrue(cmd.verbose);
    }

    @Test
    void flag_absent_staysFalse() throws CliException {
        FlagCommand cmd = new FlagCommand();
        ArgParser.inject(cmd, new String[]{});

        assertFalse(cmd.verbose);
    }

    // ──────────────────────────────────────────────
    // Default values
    // ──────────────────────────────────────────────

    @Test
    void defaultValue_applied_whenNotProvided() throws CliException {
        DefaultOptionCommand cmd = new DefaultOptionCommand();
        ArgParser.inject(cmd, new String[]{});

        assertEquals(8080, cmd.port);
    }

    @Test
    void defaultValue_overridden_whenProvided() throws CliException {
        DefaultOptionCommand cmd = new DefaultOptionCommand();
        ArgParser.inject(cmd, new String[]{"--port", "3000"});

        assertEquals(3000, cmd.port);
    }

    // ──────────────────────────────────────────────
    // Type coercion
    // ──────────────────────────────────────────────

    @Test
    void intOption_parsed() throws CliException {
        IntParamCommand cmd = new IntParamCommand();
        ArgParser.inject(cmd, new String[]{"--count", "42"});

        assertEquals(42, cmd.count);
    }

    @Test
    void intOption_invalidValue_throws() {
        IntParamCommand cmd = new IntParamCommand();

        assertThrows(CliException.class,
                () -> ArgParser.inject(cmd, new String[]{"--count", "abc"}));
    }

    // ──────────────────────────────────────────────
    // Mixed positional + options
    // ──────────────────────────────────────────────

    @Test
    void mixed_positionalAndOptions() throws CliException {
        MixedCommand cmd = new MixedCommand();
        ArgParser.inject(cmd, new String[]{"input.txt", "--force", "--output", "out.txt"});

        assertEquals("input.txt", cmd.file);
        assertTrue(cmd.force);
        assertEquals("out.txt", cmd.output);
    }

    @Test
    void mixed_optionsBeforePositional() throws CliException {
        MixedCommand cmd = new MixedCommand();
        ArgParser.inject(cmd, new String[]{"--output", "out.txt", "input.txt"});

        assertEquals("input.txt", cmd.file);
        assertEquals("out.txt", cmd.output);
    }

    // ──────────────────────────────────────────────
    // Variadic
    // ──────────────────────────────────────────────

    @Test
    void variadic_collectsRemainingArgs() throws CliException {
        VariadicCommand cmd = new VariadicCommand();
        ArgParser.inject(cmd, new String[]{"deploy", "a.jar", "b.jar", "c.jar"});

        assertEquals("deploy", cmd.target);
        assertArrayEquals(new String[]{"a.jar", "b.jar", "c.jar"}, cmd.files);
    }

    @Test
    void variadic_noExtraArgs_emptyArray() throws CliException {
        VariadicCommand cmd = new VariadicCommand();
        ArgParser.inject(cmd, new String[]{"deploy"});

        assertEquals("deploy", cmd.target);
        assertArrayEquals(new String[]{}, cmd.files);
    }
}