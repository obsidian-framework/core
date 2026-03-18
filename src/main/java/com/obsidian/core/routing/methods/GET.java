package com.obsidian.core.routing.methods;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GET
{
    String value();
    String name() default "";
}