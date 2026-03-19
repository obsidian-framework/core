package com.obsidian.core.security.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing and verification utility.
 * Uses BCrypt for secure password storage.
 */
public final class AuthPassword
{
    private AuthPassword() {}

    /**
     * Hashes a plain text password using BCrypt.
     *
     * @param password Plain text password
     * @return Bcrypt hashed password
     */
    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Verifies a plain text password against a BCrypt hash.
     *
     * @param password Plain text password
     * @param hash Bcrypt hash
     * @return true if password matches hash, false otherwise
     */
    public static boolean check(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }
}