package com.obsidian.core.security.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest
{
    @BeforeEach
    void setUp() throws Exception {
        // Clear static state between tests via reflection
        clearMap("ipCounters");
        clearMap("usernameCounters");
    }

    private void clearMap(String fieldName) throws Exception {
        Field field = LoginRateLimiter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).clear();
    }

    // ──────────────────────────────────────────────
    // isAllowed
    // ──────────────────────────────────────────────

    @Test
    void isAllowed_freshState_returnsTrue() {
        assertTrue(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));
    }

    @Test
    void isAllowed_afterOneFailure_stillAllowed() {
        LoginRateLimiter.recordFailure("1.2.3.4", "admin");

        assertTrue(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));
    }

    @Test
    void isAllowed_afterFourFailures_stillAllowed() {
        for (int i = 0; i < 4; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }

        assertTrue(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));
    }

    @Test
    void isAllowed_afterFiveFailures_lockedOut() {
        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }

        assertFalse(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));
    }

    // ──────────────────────────────────────────────
    // IP vs Username independence
    // ──────────────────────────────────────────────

    @Test
    void lockout_byIp_differentUsernameStillBlocked() {
        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "user" + i);
        }

        // IP is locked, even with a fresh username
        assertFalse(LoginRateLimiter.isAllowed("1.2.3.4", "newuser"));
    }

    @Test
    void lockout_byUsername_differentIpStillBlocked() {
        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.recordFailure("10.0.0." + i, "admin");
        }

        // Username is locked, even from a fresh IP
        assertFalse(LoginRateLimiter.isAllowed("99.99.99.99", "admin"));
    }

    @Test
    void lockout_oneIp_doesNotAffectOtherIp() {
        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }

        // Different IP, different username → should be fine
        assertTrue(LoginRateLimiter.isAllowed("5.6.7.8", "other"));
    }

    // ──────────────────────────────────────────────
    // recordSuccess resets counters
    // ──────────────────────────────────────────────

    @Test
    void recordSuccess_resetsCounters() {
        for (int i = 0; i < 4; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }

        LoginRateLimiter.recordSuccess("1.2.3.4", "admin");

        // Counter should be reset — 5 more failures needed to lock out
        assertTrue(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));

        for (int i = 0; i < 4; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }
        assertTrue(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));
    }

    // ──────────────────────────────────────────────
    // getRemainingLockoutSeconds
    // ──────────────────────────────────────────────

    @Test
    void getRemainingSeconds_notLocked_returnsZero() {
        assertEquals(0, LoginRateLimiter.getRemainingLockoutSeconds("1.2.3.4"));
    }

    @Test
    void getRemainingSeconds_locked_returnsPositive() {
        for (int i = 0; i < 5; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }

        long remaining = LoginRateLimiter.getRemainingLockoutSeconds("1.2.3.4");
        assertTrue(remaining > 0, "Should have remaining seconds");
        assertTrue(remaining <= 15 * 60, "Should not exceed 15 minutes");
    }

    // ──────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────

    @Test
    void moreFailuresThanThreshold_staysLocked() {
        for (int i = 0; i < 10; i++) {
            LoginRateLimiter.recordFailure("1.2.3.4", "admin");
        }

        assertFalse(LoginRateLimiter.isAllowed("1.2.3.4", "admin"));
    }
}