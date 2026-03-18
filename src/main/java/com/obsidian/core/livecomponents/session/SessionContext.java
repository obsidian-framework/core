package com.obsidian.core.livecomponents.session;

import spark.Session;

/**
 * Thread-local context for accessing current HTTP session.
 * Allows LiveComponents to access session without passing it through call chain.
 */
public class SessionContext
{
    /** Thread-local storage for current session */
    private static final ThreadLocal<Session> currentSession = new ThreadLocal<>();

    /**
     * Sets session for current thread.
     *
     * @param session HTTP session
     */
    public static void set(Session session) {
        currentSession.set(session);
    }

    /**
     * Gets session for current thread.
     *
     * @return HTTP session or null if not set
     */
    public static Session get() {
        return currentSession.get();
    }

    /**
     * Clears session from current thread.
     * Should be called after request processing to prevent memory leaks.
     */
    public static void clear() {
        currentSession.remove();
    }
}