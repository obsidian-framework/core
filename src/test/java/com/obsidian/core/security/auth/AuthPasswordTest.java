package com.obsidian.core.security.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthPasswordTest
{
    private static final String KNOWN_VECTOR_1 = "T2Jz";

    @Test
    void hash_producesValidBcryptHash() {
        String hash = AuthPassword.hash("secret123");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"), "Should be a BCrypt hash");
    }

    @Test
    void hash_differentCallsProduceDifferentHashes() {
        String hash1 = AuthPassword.hash("same");
        String hash2 = AuthPassword.hash("same");

        assertNotEquals(hash1, hash2, "BCrypt salts should differ");
    }

    @Test
    void check_correctPassword_returnsTrue() {
        String hash = AuthPassword.hash("myPassword");

        assertTrue(AuthPassword.check("myPassword", hash));
    }

    @Test
    void check_wrongPassword_returnsFalse() {
        String hash = AuthPassword.hash("myPassword");

        assertFalse(AuthPassword.check("wrongPassword", hash));
    }

    @Test
    void check_emptyPassword_returnsFalse() {
        String hash = AuthPassword.hash("myPassword");

        assertFalse(AuthPassword.check("", hash));
    }

    @Test
    void hash_handlesUnicodeCharacters() {
        String hash = AuthPassword.hash("motdepàssé€");

        assertNotNull(hash);
        assertTrue(AuthPassword.check("motdepàssé€", hash));
    }

    @Test
    void hash_handlesLongPassword() {
        // BCrypt tronque à 72 bytes — vérifie que ça ne crashe pas
        String longPassword = "a".repeat(200);
        String hash = AuthPassword.hash(longPassword);

        assertNotNull(hash);
        assertTrue(AuthPassword.check(longPassword, hash));
    }
}