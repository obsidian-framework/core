package com.obsidian.core.security.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthPasswordTest
{
    @Test
    void hashPassword_producesValidBcryptHash() {
        String hash = Auth.hashPassword("secret123");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"), "Should be a BCrypt hash");
    }

    @Test
    void hashPassword_differentCallsProduceDifferentHashes() {
        String hash1 = Auth.hashPassword("same");
        String hash2 = Auth.hashPassword("same");

        assertNotEquals(hash1, hash2, "BCrypt salts should differ");
    }

    @Test
    void checkPassword_correctPassword_returnsTrue() {
        String hash = Auth.hashPassword("myPassword");

        assertTrue(Auth.checkPassword("myPassword", hash));
    }

    @Test
    void checkPassword_wrongPassword_returnsFalse() {
        String hash = Auth.hashPassword("myPassword");

        assertFalse(Auth.checkPassword("wrongPassword", hash));
    }

    @Test
    void checkPassword_emptyPassword_returnsFalse() {
        String hash = Auth.hashPassword("myPassword");

        assertFalse(Auth.checkPassword("", hash));
    }

    @Test
    void hashPassword_handlesUnicodeCharacters() {
        String hash = Auth.hashPassword("motdepàssé€");

        assertNotNull(hash);
        assertTrue(Auth.checkPassword("motdepàssé€", hash));
    }

    @Test
    void hashPassword_handlesLongPassword() {
        // BCrypt tronque à 72 bytes — vérifie que ça ne crashe pas
        String longPassword = "a".repeat(200);
        String hash = Auth.hashPassword(longPassword);

        assertNotNull(hash);
        assertTrue(Auth.checkPassword(longPassword, hash));
    }
}