package com.obsidian.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an event listener container. The class is scanned at boot
 * time for methods annotated with {@link On}; each such method is registered
 * <h2>Example</h2>
 * <pre>
 * {@literal @}Listener
 *  public class SendWelcomeEmail {
 *
 *     {@literal @}Inject private MailService mail;
 *
 *     {@literal @}On(UserRegistered.class)
 *      public void handle(UserRegistered event) {
 *          mail.send(event.getUser().getEmail(), "Welcome!", "...");
 *      }
 *  }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Listener
{
}
