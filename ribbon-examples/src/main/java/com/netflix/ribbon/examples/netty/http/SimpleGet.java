package com.netflix.ribbon.examples.netty.http;

import rx.util.functions.Action1;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder.NettyHttpLoadBalancingClientBuilder;
import com.netflix.serialization.StringDeserializer;
import com.netflix.serialization.TypeDef;

public class SimpleGet {
    public static void main(String[] args) {
        NettyHttpClient client = NettyHttpLoadBalancingClientBuilder.newBuilder()
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri("http://www.google.com:80")
                .build();
        client.createFullHttpResponseObservable(request)
            .toBlockingObservable()
            .forEach(new Action1<HttpResponse>() {
                @Override
                public void call(HttpResponse t1) {
                    System.out.println("Status code: " + t1.getStatus());
                    try {
                        System.out.println("Response content: " + t1.getEntity(TypeDef.fromClass(String.class), StringDeserializer.getInstance()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
    }
}
