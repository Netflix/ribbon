package com.netflix.ribbonclientextensions;

import com.netflix.ribbonclientextensions.http.HttpResourceGroup;

import io.reactivex.netty.protocol.http.client.HttpClient;

public final class Ribbon {
    
    private Ribbon() {
    }
 
    public static HttpResourceGroup createHttpResourceGroup(String name) {
        return new HttpResourceGroup(name);
    }
    
    public static <I, O, T> T from(Class<T> contract, HttpClient<I, O> transportClient) {
        return null;
    } 
}
