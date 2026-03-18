package com.obsidian.core.security.user;

public interface UserDetails
{
    Object getId();
    String getUsername();
    String getPassword();
    String getRole();

    default boolean isEnabled() { return true; }
}
