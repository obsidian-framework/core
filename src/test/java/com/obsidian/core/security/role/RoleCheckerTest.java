package com.obsidian.core.security.role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoleCheckerTest
{
    @BeforeEach
    void setUp() throws Exception {
        clearField("pathToRole");
        clearField("loginRequiredPaths");
        clearField("tokenRequiredPaths");
        clearField("tokenPathToRole");
    }

    @SuppressWarnings("unchecked")
    private void clearField(String name) throws Exception {
        Field f = RoleChecker.class.getDeclaredField(name);
        f.setAccessible(true);
        Object collection = f.get(null);
        if (collection instanceof Map<?, ?> map) map.clear();
        else if (collection instanceof Set<?> set) set.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(String name) throws Exception {
        Field f = RoleChecker.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(null);
    }

    // ──────────────────────────────────────────────
    // registerPathWithRole
    // ──────────────────────────────────────────────

    @Test
    void registerPathWithRole_storesMapping() throws Exception {
        RoleChecker.registerPathWithRole("/admin", "ADMIN");

        Map<String, String> pathToRole = getField("pathToRole");
        assertEquals("ADMIN", pathToRole.get("/admin"));
    }

    @Test
    void registerPathWithRole_multipleRoutes() throws Exception {
        RoleChecker.registerPathWithRole("/admin", "ADMIN");
        RoleChecker.registerPathWithRole("/mod", "MODERATOR");

        Map<String, String> pathToRole = getField("pathToRole");
        assertEquals(2, pathToRole.size());
    }

    // ──────────────────────────────────────────────
    // registerLoginRequired
    // ──────────────────────────────────────────────

    @Test
    void registerLoginRequired_storesPath() throws Exception {
        RoleChecker.registerLoginRequired("/dashboard");

        Set<String> paths = getField("loginRequiredPaths");
        assertTrue(paths.contains("/dashboard"));
    }

    @Test
    void registerLoginRequired_noDuplicates() throws Exception {
        RoleChecker.registerLoginRequired("/dashboard");
        RoleChecker.registerLoginRequired("/dashboard");

        Set<String> paths = getField("loginRequiredPaths");
        assertEquals(1, paths.size());
    }

    // ──────────────────────────────────────────────
    // registerTokenRequired
    // ──────────────────────────────────────────────

    @Test
    void registerTokenRequired_storesPath() throws Exception {
        RoleChecker.registerTokenRequired("/api/users");

        Set<String> paths = getField("tokenRequiredPaths");
        assertTrue(paths.contains("/api/users"));
    }

    // ──────────────────────────────────────────────
    // registerTokenPathWithRole
    // ──────────────────────────────────────────────

    @Test
    void registerTokenPathWithRole_storesMapping() throws Exception {
        RoleChecker.registerTokenPathWithRole("/api/admin", "ADMIN");

        Map<String, String> tokenPaths = getField("tokenPathToRole");
        assertEquals("ADMIN", tokenPaths.get("/api/admin"));
    }

    // ──────────────────────────────────────────────
    // Independence between registries
    // ──────────────────────────────────────────────

    @Test
    void differentRegistries_areIndependent() throws Exception {
        RoleChecker.registerPathWithRole("/admin", "ADMIN");
        RoleChecker.registerLoginRequired("/dashboard");
        RoleChecker.registerTokenRequired("/api/data");
        RoleChecker.registerTokenPathWithRole("/api/admin", "ADMIN");

        Map<String, String> pathToRole = getField("pathToRole");
        Set<String> loginPaths = getField("loginRequiredPaths");
        Set<String> tokenPaths = getField("tokenRequiredPaths");
        Map<String, String> tokenPathToRole = getField("tokenPathToRole");

        assertEquals(1, pathToRole.size());
        assertEquals(1, loginPaths.size());
        assertEquals(1, tokenPaths.size());
        assertEquals(1, tokenPathToRole.size());
    }
}