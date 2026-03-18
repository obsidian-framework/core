package com.obsidian.core.routing;

import com.obsidian.core.http.controller.annotations.ApiController;
import com.obsidian.core.http.controller.annotations.Controller;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RouteLoaderTest
{
    // ──────────────────────────────────────────────
    // Test fixtures — annotated controller stubs
    // ──────────────────────────────────────────────

    @Controller
    static class NoPrefix {}

    @Controller("/users")
    static class WithPrefix {}

    @Controller("/users/")
    static class TrailingSlash {}

    @Controller("users")
    static class NoLeadingSlash {}

    @Controller("  /admin  ")
    static class WhitespacePrefix {}

    @Controller("/api///")
    static class MultipleTrailingSlashes {}

    @ApiController("/api/v1")
    static class ApiWithPrefix {}

    @ApiController
    static class ApiNoPrefix {}

    static class NoAnnotation {}

    // ──────────────────────────────────────────────
    // resolvePrefix tests (accessed via reflection)
    // ──────────────────────────────────────────────

    private String resolvePrefix(Class<?> controllerClass) throws Exception {
        Method method = RouteLoader.class.getDeclaredMethod("resolvePrefix", Class.class);
        method.setAccessible(true);
        return (String) method.invoke(null, controllerClass);
    }

    @Test
    void resolvePrefix_controllerNoValue_returnsEmpty() throws Exception {
        assertEquals("", resolvePrefix(NoPrefix.class));
    }

    @Test
    void resolvePrefix_controllerWithValue_returnsPath() throws Exception {
        assertEquals("/users", resolvePrefix(WithPrefix.class));
    }

    @Test
    void resolvePrefix_stripsTrailingSlash() throws Exception {
        assertEquals("/users", resolvePrefix(TrailingSlash.class));
    }

    @Test
    void resolvePrefix_addsLeadingSlash() throws Exception {
        assertEquals("/users", resolvePrefix(NoLeadingSlash.class));
    }

    @Test
    void resolvePrefix_trimsWhitespace() throws Exception {
        assertEquals("/admin", resolvePrefix(WhitespacePrefix.class));
    }

    @Test
    void resolvePrefix_stripsMultipleTrailingSlashes() throws Exception {
        assertEquals("/api", resolvePrefix(MultipleTrailingSlashes.class));
    }

    @Test
    void resolvePrefix_apiController_returnsPath() throws Exception {
        assertEquals("/api/v1", resolvePrefix(ApiWithPrefix.class));
    }

    @Test
    void resolvePrefix_apiControllerNoValue_returnsEmpty() throws Exception {
        assertEquals("", resolvePrefix(ApiNoPrefix.class));
    }

    @Test
    void resolvePrefix_noAnnotation_returnsEmpty() throws Exception {
        assertEquals("", resolvePrefix(NoAnnotation.class));
    }

    // ──────────────────────────────────────────────
    // Route (named routes registry)
    // ──────────────────────────────────────────────

    @Test
    void registerNamedRoute_andGetPath() {
        Route.registerNamedRoute("home", "/");
        assertEquals("/", Route.getPath("home"));
    }

    @Test
    void registerNamedRoute_emptyName_ignored() {
        int before = Route.getAllRoutes().size();
        Route.registerNamedRoute("", "/nothing");
        assertEquals(before, Route.getAllRoutes().size());
    }

    @Test
    void registerNamedRoute_nullName_ignored() {
        int before = Route.getAllRoutes().size();
        Route.registerNamedRoute(null, "/nothing");
        assertEquals(before, Route.getAllRoutes().size());
    }

    @Test
    void hasRoute_existingRoute_returnsTrue() {
        Route.registerNamedRoute("test.exists", "/test");
        assertTrue(Route.hasRoute("test.exists"));
    }

    @Test
    void hasRoute_unknownRoute_returnsFalse() {
        assertFalse(Route.hasRoute("route.that.does.not.exist.xyz"));
    }
}