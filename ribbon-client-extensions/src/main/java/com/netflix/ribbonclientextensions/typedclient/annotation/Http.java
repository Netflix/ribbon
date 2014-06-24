package com.netflix.ribbonclientextensions.typedclient.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Tomasz Bak
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Http {

    enum HttpMethod {
        GET,
        POST
    };

    HttpMethod method();

    String path() default "";
}
