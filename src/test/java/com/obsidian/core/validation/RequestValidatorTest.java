package com.obsidian.core.validation;

import org.junit.jupiter.api.Test;
import spark.Request;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestValidatorTest
{
    /**
     * Creates a mocked Request that returns the given params for queryParams().
     */
    private Request fakeRequest(Map<String, String> params) {
        Request req = mock(Request.class);
        when(req.queryParams(anyString())).thenAnswer(inv -> params.get(inv.getArgument(0)));
        return req;
    }

    // ──────────────────────────────────────────────
    // required
    // ──────────────────────────────────────────────

    @Test
    void required_present_passes() {
        Request req = fakeRequest(Map.of("name", "Alice"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("name", "required"));

        assertEquals("Alice", data.get("name"));
    }

    @Test
    void required_missing_throws() {
        Request req = fakeRequest(Map.of());

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("name", "required"))
        );
        assertTrue(ex.getErrors().has("name"));
    }

    @Test
    void required_blank_throws() {
        Request req = fakeRequest(Map.of("name", "   "));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("name", "required"))
        );
        assertTrue(ex.getErrors().has("name"));
    }

    // ──────────────────────────────────────────────
    // email
    // ──────────────────────────────────────────────

    @Test
    void email_valid_passes() {
        Request req = fakeRequest(Map.of("email", "test@example.com"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("email", "email"));

        assertEquals("test@example.com", data.get("email"));
    }

    @Test
    void email_invalid_throws() {
        Request req = fakeRequest(Map.of("email", "not-an-email"));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("email", "email"))
        );
        assertTrue(ex.getErrors().has("email"));
    }

    @Test
    void email_null_skipsValidation() {
        // email rule without required → null is OK
        Request req = fakeRequest(Map.of());

        // Should not throw — email is optional if not required
        Map<String, Object> data = RequestValidator.validateSafe(req, Map.of("email", "email")).getData();
        assertFalse(data.containsKey("email"));
    }

    // ──────────────────────────────────────────────
    // min / max
    // ──────────────────────────────────────────────

    @Test
    void min_tooShort_throws() {
        Request req = fakeRequest(Map.of("password", "ab"));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("password", "min:3"))
        );
        assertTrue(ex.getErrors().has("password"));
    }

    @Test
    void min_exactLength_passes() {
        Request req = fakeRequest(Map.of("password", "abc"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("password", "min:3"));
        assertEquals("abc", data.get("password"));
    }

    @Test
    void max_tooLong_throws() {
        Request req = fakeRequest(Map.of("code", "abcdef"));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("code", "max:5"))
        );
        assertTrue(ex.getErrors().has("code"));
    }

    @Test
    void max_exactLength_passes() {
        Request req = fakeRequest(Map.of("code", "abcde"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("code", "max:5"));
        assertEquals("abcde", data.get("code"));
    }

    // ──────────────────────────────────────────────
    // numeric / integer
    // ──────────────────────────────────────────────

    @Test
    void numeric_validNumber_passes() {
        Request req = fakeRequest(Map.of("price", "19.99"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("price", "numeric"));
        assertEquals("19.99", data.get("price"));
    }

    @Test
    void numeric_notANumber_throws() {
        Request req = fakeRequest(Map.of("price", "abc"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("price", "numeric")));
    }

    @Test
    void integer_valid_passes() {
        Request req = fakeRequest(Map.of("age", "25"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("age", "integer"));
        assertEquals("25", data.get("age"));
    }

    @Test
    void integer_decimal_throws() {
        Request req = fakeRequest(Map.of("age", "25.5"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("age", "integer")));
    }

    // ──────────────────────────────────────────────
    // alpha / alphanumeric
    // ──────────────────────────────────────────────

    @Test
    void alpha_lettersOnly_passes() {
        Request req = fakeRequest(Map.of("name", "Alice"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("name", "alpha"));
        assertEquals("Alice", data.get("name"));
    }

    @Test
    void alpha_withNumbers_throws() {
        Request req = fakeRequest(Map.of("name", "Alice123"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("name", "alpha")));
    }

    @Test
    void alphanumeric_passes() {
        Request req = fakeRequest(Map.of("code", "abc123"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("code", "alphanumeric"));
        assertEquals("abc123", data.get("code"));
    }

    @Test
    void alphanumeric_withSymbols_throws() {
        Request req = fakeRequest(Map.of("code", "abc-123"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("code", "alphanumeric")));
    }

    // ──────────────────────────────────────────────
    // in
    // ──────────────────────────────────────────────

    @Test
    void in_validOption_passes() {
        Request req = fakeRequest(Map.of("role", "admin"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("role", "in:admin,user,mod"));
        assertEquals("admin", data.get("role"));
    }

    @Test
    void in_invalidOption_throws() {
        Request req = fakeRequest(Map.of("role", "superadmin"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("role", "in:admin,user,mod")));
    }

    // ──────────────────────────────────────────────
    // regex
    // ──────────────────────────────────────────────

    @Test
    void regex_matches_passes() {
        Request req = fakeRequest(Map.of("zip", "75001"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("zip", "regex:^\\d{5}$"));
        assertEquals("75001", data.get("zip"));
    }

    @Test
    void regex_noMatch_throws() {
        Request req = fakeRequest(Map.of("zip", "ABC"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("zip", "regex:^\\d{5}$")));
    }

    // ──────────────────────────────────────────────
    // confirmed
    // ──────────────────────────────────────────────

    @Test
    void confirmed_matching_passes() {
        Request req = fakeRequest(Map.of("password", "secret", "password_confirmation", "secret"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("password", "confirmed"));
        assertEquals("secret", data.get("password"));
    }

    @Test
    void confirmed_mismatch_throws() {
        Request req = fakeRequest(Map.of("password", "secret", "password_confirmation", "different"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("password", "confirmed")));
    }

    // ──────────────────────────────────────────────
    // Chained rules (pipe-separated)
    // ──────────────────────────────────────────────

    @Test
    void chainedRules_allPass() {
        Request req = fakeRequest(Map.of("email", "test@example.com"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("email", "required|email"));
        assertEquals("test@example.com", data.get("email"));
    }

    @Test
    void chainedRules_firstFails_stopsEarly() {
        // required fails → email rule never runs
        Request req = fakeRequest(Map.of());

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("email", "required|email"))
        );

        // Only one error: required
        assertEquals(1, ex.getErrors().count());
        assertTrue(ex.getErrors().get("email").contains("required"));
    }

    // ──────────────────────────────────────────────
    // validateSafe (no exception)
    // ──────────────────────────────────────────────

    @Test
    void validateSafe_valid_returnsIsValid() {
        Request req = fakeRequest(Map.of("name", "Alice"));

        ValidationResult result = RequestValidator.validateSafe(req, Map.of("name", "required"));

        assertTrue(result.isValid());
        assertFalse(result.fails());
        assertEquals("Alice", result.getData().get("name"));
    }

    @Test
    void validateSafe_invalid_returnsErrors() {
        Request req = fakeRequest(Map.of());

        ValidationResult result = RequestValidator.validateSafe(req, Map.of("name", "required"));

        assertFalse(result.isValid());
        assertTrue(result.fails());
        assertTrue(result.getErrors().has("name"));
    }

    // ──────────────────────────────────────────────
    // between
    // ──────────────────────────────────────────────

    @Test
    void between_numericValue_inRange_passes() {
        Request req = fakeRequest(Map.of("age", "25"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("age", "between:18,65"));
        assertEquals("25", data.get("age"));
    }

    @Test
    void between_numericValue_outOfRange_throws() {
        Request req = fakeRequest(Map.of("age", "10"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("age", "between:18,65")));
    }

    @Test
    void between_stringLength_inRange_passes() {
        Request req = fakeRequest(Map.of("name", "Alice"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("name", "between:3,10"));
        assertEquals("Alice", data.get("name"));
    }

    // ──────────────────────────────────────────────
    // url
    // ──────────────────────────────────────────────

    @Test
    void url_valid_passes() {
        Request req = fakeRequest(Map.of("site", "https://example.com"));

        Map<String, Object> data = RequestValidator.validate(req, Map.of("site", "url"));
        assertEquals("https://example.com", data.get("site"));
    }

    @Test
    void url_invalid_throws() {
        Request req = fakeRequest(Map.of("site", "not a url at all"));

        assertThrows(ValidationException.class,
                () -> RequestValidator.validate(req, Map.of("site", "url")));
    }

    // ──────────────────────────────────────────────
    // Unknown rule
    // ──────────────────────────────────────────────

    @Test
    void unknownRule_throwsIllegalArgument() {
        Request req = fakeRequest(Map.of("name", "test"));

        assertThrows(IllegalArgumentException.class,
                () -> RequestValidator.validate(req, Map.of("name", "doesnotexist")));
    }
}