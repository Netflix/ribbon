package com.netflix.ribbonclientextensions;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

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
        return null;
    } 
}
