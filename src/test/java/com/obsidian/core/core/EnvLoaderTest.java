package com.obsidian.core.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnvLoaderTest
{
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton between tests
        resetSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field instanceField = EnvLoader.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private void writeEnvFile(String content) throws IOException {
        Files.writeString(tempDir.resolve(".env"), content);
    }

    private EnvLoader loadWith(String envContent) throws IOException {
        writeEnvFile(envContent);
        EnvLoader loader = new EnvLoader(tempDir);
        loader.load();
        return loader;
    }

    // ──────────────────────────────────────────────
    // Basic loading
    // ──────────────────────────────────────────────

    @Test
    void load_readsEnvFile() throws IOException {
        EnvLoader loader = loadWith("APP_NAME=Obsidian\nPORT=8080");

        assertEquals("Obsidian", loader.get("APP_NAME"));
        assertEquals("8080", loader.get("PORT"));
    }

    @Test
    void get_missingKey_returnsNull() throws IOException {
        EnvLoader loader = loadWith("APP_NAME=Obsidian");

        assertNull(loader.get("NONEXISTENT"));
    }

    @Test
    void get_withDefault_returnsFallback() throws IOException {
        EnvLoader loader = loadWith("APP_NAME=Obsidian");

        assertEquals("fallback", loader.get("MISSING_KEY", "fallback"));
    }

    @Test
    void get_withDefault_existingKey_returnsValue() throws IOException {
        EnvLoader loader = loadWith("APP_NAME=Obsidian");

        assertEquals("Obsidian", loader.get("APP_NAME", "fallback"));
    }

    // ──────────────────────────────────────────────
    // getRequired
    // ──────────────────────────────────────────────

    @Test
    void getRequired_existingKey_returnsValue() throws IOException {
        EnvLoader loader = loadWith("SECRET=abc123");

        assertEquals("abc123", loader.getRequired("SECRET"));
    }

    @Test
    void getRequired_missingKey_throws() throws IOException {
        EnvLoader loader = loadWith("OTHER=value");

        assertThrows(IllegalStateException.class, () -> loader.getRequired("SECRET"));
    }

    @Test
    void getRequired_blankValue_throws() throws IOException {
        EnvLoader loader = loadWith("SECRET=   ");

        assertThrows(IllegalStateException.class, () -> loader.getRequired("SECRET"));
    }

    // ──────────────────────────────────────────────
    // getInt
    // ──────────────────────────────────────────────

    @Test
    void getInt_validNumber_returnsInt() throws IOException {
        EnvLoader loader = loadWith("PORT=3000");

        assertEquals(3000, loader.getInt("PORT", 8080));
    }

    @Test
    void getInt_missingKey_returnsDefault() throws IOException {
        EnvLoader loader = loadWith("OTHER=value");

        assertEquals(8080, loader.getInt("PORT", 8080));
    }

    @Test
    void getInt_invalidNumber_returnsDefault() throws IOException {
        EnvLoader loader = loadWith("PORT=notanumber");

        assertEquals(8080, loader.getInt("PORT", 8080));
    }

    // ──────────────────────────────────────────────
    // getBoolean
    // ──────────────────────────────────────────────

    @Test
    void getBoolean_true_returnsTrue() throws IOException {
        EnvLoader loader = loadWith("DEBUG=true");

        assertTrue(loader.getBoolean("DEBUG", false));
    }

    @Test
    void getBoolean_false_returnsFalse() throws IOException {
        EnvLoader loader = loadWith("DEBUG=false");

        assertFalse(loader.getBoolean("DEBUG", true));
    }

    @Test
    void getBoolean_missing_returnsDefault() throws IOException {
        EnvLoader loader = loadWith("OTHER=value");

        assertTrue(loader.getBoolean("DEBUG", true));
    }

    // ──────────────────────────────────────────────
    // Singleton behavior
    // ──────────────────────────────────────────────

    @Test
    void getInstance_afterLoad_returnsSameInstance() throws IOException {
        EnvLoader loader = loadWith("APP=test");

        assertSame(loader, EnvLoader.getInstance());
    }

    @Test
    void getInstance_beforeLoad_throws() {
        assertThrows(IllegalStateException.class, EnvLoader::getInstance);
    }

    @Test
    void load_calledTwice_doesNotOverride() throws IOException {
        writeEnvFile("APP=first");
        EnvLoader first = new EnvLoader(tempDir);
        first.load();

        // Create a second loader — load() should no-op
        EnvLoader second = new EnvLoader(tempDir);
        second.load();

        assertSame(first, EnvLoader.getInstance());
    }

    // ──────────────────────────────────────────────
    // ensureLoaded guard
    // ──────────────────────────────────────────────

    @Test
    void get_beforeLoad_throws() {
        EnvLoader loader = new EnvLoader(tempDir);

        assertThrows(IllegalStateException.class, () -> loader.get("KEY"));
    }
}