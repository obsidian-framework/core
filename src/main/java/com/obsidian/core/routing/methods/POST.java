package com.obsidian.core.routing.methods;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface POST
{
    String value();
    String name() default "";
}