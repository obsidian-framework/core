package com.obsidian.core.http.middleware.annotations;

import com.obsidian.core.http.middleware.Middleware;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Before {
    Class<? extends Middleware>[] value();
}
