package fr.kainovaii.obsidian.security.user;

public interface UserDetailsService
{
    UserDetails loadByUsername(String username);

    UserDetails loadById(Object id);
}