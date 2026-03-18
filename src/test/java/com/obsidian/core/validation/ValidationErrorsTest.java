package com.obsidian.core.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationErrorsTest
{
    private ValidationErrors errors;

    @BeforeEach
    void setUp() {
        errors = new ValidationErrors();
    }

    @Test
    void empty_hasNoErrors() {
        assertFalse(errors.hasErrors());
        assertEquals(0, errors.count());
    }

    @Test
    void add_createsError() {
        errors.add("email", "Invalid email");

        assertTrue(errors.hasErrors());
        assertTrue(errors.has("email"));
        assertEquals("Invalid email", errors.get("email"));
    }

    @Test
    void add_onlyKeepsFirstErrorPerField() {
        errors.add("email", "First error");
        errors.add("email", "Second error");

        assertEquals("First error", errors.get("email"));
        assertEquals(1, errors.count());
    }

    @Test
    void has_unknownField_returnsFalse() {
        assertFalse(errors.has("unknown"));
    }

    @Test
    void get_unknownField_returnsNull() {
        assertNull(errors.get("unknown"));
    }

    @Test
    void count_multipleFields() {
        errors.add("name", "Required");
        errors.add("email", "Invalid");
        errors.add("age", "Too young");

        assertEquals(3, errors.count());
    }

    @Test
    void first_returnsAnyError() {
        errors.add("name", "Required");

        assertNotNull(errors.first());
    }

    @Test
    void first_empty_returnsNull() {
        assertNull(errors.first());
    }

    @Test
    void all_returnsCopy() {
        errors.add("name", "Required");

        var copy = errors.all();
        copy.put("hack", "injected");

        assertFalse(errors.has("hack"), "Modifying copy should not affect original");
    }
}