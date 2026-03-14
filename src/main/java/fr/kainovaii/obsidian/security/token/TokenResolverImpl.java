package fr.kainovaii.obsidian.security.token;

import java.lang.annotation.*;

/**
 * Marks a class as the application's {@link TokenResolver} implementation.
 * Auto-detected by the framework at startup — no manual registration required.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TokenResolverImpl
{
}