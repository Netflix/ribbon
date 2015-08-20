package com.netflix.ribbon.proxy.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Allen Wang
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientProperties {
    @interface Property {
        String name();
        String value();
    }

    Property[] properties() default {};

    boolean exportToArchaius() default false;
}
