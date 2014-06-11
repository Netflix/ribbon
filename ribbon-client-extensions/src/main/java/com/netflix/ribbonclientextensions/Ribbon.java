package com.netflix.ribbonclientextensions;

import io.reactivex.netty.protocol.http.client.HttpClient;

public final class Ribbon {
    
    private Ribbon() {
    }
 
    public static <I, O> RibbonHttpClient<I, O> from(HttpClient<I, O> transportClient) {
        return null;
    }
 
    public static <I, O, T> T create(Class<T> contract, HttpClient<I, O> transportClient) {
        return null;
    }
 
}
