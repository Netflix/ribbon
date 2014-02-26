package com.netflix.ribbon.examples.netty.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpRequest;
import io.reactivex.netty.protocol.http.client.HttpResponse;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rx.util.functions.Action1;

import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder.NettyHttpLoadBalancingClientBuilder;

public class SimpleGet {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings
    public static void main(String[] args) throws Exception {
        NettyHttpClient client = NettyHttpLoadBalancingClientBuilder.newBuilder()
                .build();
        HttpRequest<ByteBuf> request = HttpRequest.createGet("/");
                
        final CountDownLatch latch = new CountDownLatch(1);
        client.createFullHttpResponseObservable("www.google.com", 80, request)
            .toBlockingObservable()
            .forEach(new Action1<HttpResponse<ByteBuf>>() {
                @Override
                public void call(HttpResponse<ByteBuf> t1) {
                    System.out.println("Status code: " + t1.getStatus());
                    t1.getContent().subscribe(new Action1<ByteBuf>() {

                        @Override
                        public void call(ByteBuf content) {
                            System.out.println("Response content: " + content.toString(Charset.defaultCharset()));
                            latch.countDown();
                        }
                        
                    });
                }
            });
        latch.await(2, TimeUnit.SECONDS);
    }
}
