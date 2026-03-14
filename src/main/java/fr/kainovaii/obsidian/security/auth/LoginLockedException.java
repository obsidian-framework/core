package fr.kainovaii.obsidian.security.auth;

/**
 * Thrown when a login attempt is blocked due to too many failed attempts.
 *
 * @see LoginRateLimiter
 */
public class LoginLockedException extends RuntimeException
{
    /**
     * @param message Human-readable message including remaining lockout duration
     */
    public LoginLockedException(String message) {
        super(message);
    }
}