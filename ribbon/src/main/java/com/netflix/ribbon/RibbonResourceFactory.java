package com.netflix.ribbon;

public interface RibbonResourceFactory {
    <T> T from(Class<T> contract);
}
