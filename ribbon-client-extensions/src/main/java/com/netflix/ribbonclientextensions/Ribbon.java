package com.netflix.ribbonclientextensions;

import rx.functions.Action1;

import com.netflix.client.config.ClientConfigBuilder;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;

public final class Ribbon {
    
    private Ribbon() {
    }
 
    public static <I, O> RibbonHttpClient<I, O> newHttpClient(HttpClient<I, O> transportClient) {
        return null;
    }
 
    public static <I, O, T> T newHttpClient(Class<T> contract, HttpClient<I, O> transportClient) {
        return null;
    }
 
}
