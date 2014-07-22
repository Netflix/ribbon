package com.netflix.ribbon;

public class DefaultRibbonResourceFactory implements RibbonResourceFactory {
    @Override
    public <T> T from(Class<T> contract) {
        return Ribbon.from(contract);
    }
}
