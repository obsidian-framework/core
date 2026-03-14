package fr.kainovaii.obsidian.security.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute force protection for login attempts.
 * Tracks failed attempts per IP and per username independently.
 */
public final class LoginRateLimiter
{
    /** Maximum consecutive failed attempts before lockout */
    private static final int MAX_ATTEMPTS = 5;

    /** Lockout duration in milliseconds (15 minutes) */
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000;

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    /** Failed attempt counters keyed by IP */
    private static final Map<String, AttemptCounter> ipCounters = new ConcurrentHashMap<>();

    /** Failed attempt counters keyed by username */
    private static final Map<String, AttemptCounter> usernameCounters = new ConcurrentHashMap<>();

    private LoginRateLimiter() {}

    /**
     * Checks if the given IP or username is currently locked out.
     *
     * @param ip Client IP address
     * @param username Submitted username
     * @return true if access is allowed, false if locked out
     */
    public static boolean isAllowed(String ip, String username)
    {
        AttemptCounter ipCounter = ipCounters.get(ip);
        if (ipCounter != null && ipCounter.isLockedOut()) {
            logger.warn("Login blocked — IP locked out: {}", ip);
            return false;
        }

        AttemptCounter usernameCounter = usernameCounters.get(username);
        if (usernameCounter != null && usernameCounter.isLockedOut()) {
            logger.warn("Login blocked — username locked out: {}", username);
            return false;
        }

        return true;
    }

    /**
     * Records a failed login attempt for the given IP and username.
     * Triggers lockout if {@link #MAX_ATTEMPTS} is reached.
     *
     * @param ip Client IP address
     * @param username Submitted username
     */
    public static void recordFailure(String ip, String username)
    {
        ipCounters.computeIfAbsent(ip, k -> new AttemptCounter()).increment();
        usernameCounters.computeIfAbsent(username, k -> new AttemptCounter()).increment();

        AttemptCounter ipCounter = ipCounters.get(ip);
        if (ipCounter.isLockedOut()) {
            logger.warn("IP locked out after {} failed attempts: {}", MAX_ATTEMPTS, ip);
        }
    }

    /**
     * Resets failed attempt counters for the given IP and username.
     * Should be called on successful login.
     *
     * @param ip Client IP address
     * @param username Submitted username
     */
    public static void recordSuccess(String ip, String username)
    {
        ipCounters.remove(ip);
        usernameCounters.remove(username);
    }

    /**
     * Returns remaining seconds before the lockout expires for the given IP.
     * Returns 0 if not locked out.
     *
     * @param ip Client IP address
     * @return Seconds remaining in lockout, or 0
     */
    public static long getRemainingLockoutSeconds(String ip)
    {
        AttemptCounter counter = ipCounters.get(ip);
        if (counter == null || !counter.isLockedOut()) return 0;
        return counter.getRemainingSeconds();
    }

    private static class AttemptCounter
    {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long lockedUntil = 0;

        /**
         * Increments the failure counter and triggers lockout if threshold is reached.
         */
        public void increment()
        {
            int attempts = count.incrementAndGet();
            if (attempts >= MAX_ATTEMPTS) {
                lockedUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            }
        }

        /**
         * Returns true if currently locked out.
         * Resets automatically when lockout expires.
         */
        public boolean isLockedOut()
        {
            if (lockedUntil == 0) return false;
            if (System.currentTimeMillis() > lockedUntil) {
                lockedUntil = 0;
                count.set(0);
                return false;
            }
            return true;
        }

        /**
         * Returns remaining lockout duration in seconds.
         */
        public long getRemainingSeconds()
        {
            return Math.max(0, (lockedUntil - System.currentTimeMillis()) / 1000);
        }
    }
}