package fr.kainovaii.obsidian.livecomponents.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a component prop — initialized at mount time from the parent template.
 * Props are injected before {@code onMount()} is called and are not serialized into
 * the client-side state, making them read-only from the client's perspective.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prop {
}