package com.netflix.ribbonclientextensions;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import com.netflix.ribbonclientextensions.typedclient.RibbonDynamicProxy;

public final class Ribbon {

    private Ribbon() {
    }

    public static HttpResourceGroup createHttpResourceGroup(String name) {
        return new HttpResourceGroup(name);
    }

    public static HttpResourceGroup createHttpResourceGroup(String name, ClientOptions options) {
        return new HttpResourceGroup(name, options);
    }

    public static <T> T from(Class<T> contract) {
        return RibbonDynamicProxy.newInstance(contract, null);
    }

    public static <T> T from(Class<T> contract, HttpResourceGroup httpResourceGroup) {
        return RibbonDynamicProxy.newInstance(contract, httpResourceGroup);
    }
}
