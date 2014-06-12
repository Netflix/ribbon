package com.netflix.ribbonclientextensions;

import io.reactivex.netty.protocol.http.client.HttpClient;

public final class Ribbon {
    
    private Ribbon() {
    }
 
    public static <I, O> HttpRequestTemplate<I, O> newHttpRequestTemplate(HttpClient<I, O> transportClient) {
        return null;
    }
 
    public static <I, O, T> T from(Class<T> contract, HttpClient<I, O> transportClient) {
        return null;
    }
 
}
