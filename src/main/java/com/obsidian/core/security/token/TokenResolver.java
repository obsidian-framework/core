package com.obsidian.core.security.token;

import com.obsidian.core.security.user.UserDetails;

/**
 * Token-based authentication resolver.
 * Implement this interface to validate Bearer tokens and resolve the associated user.
 */
public interface TokenResolver
{
    /**
     * Resolves a Bearer token to a UserDetails instance.
     *
     * @param token Raw Bearer token from Authorization header
     * @return UserDetails if token is valid, null otherwise
     */
    UserDetails resolve(String token);
}