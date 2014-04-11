package com.netflix.ribbon.examples.netty.http;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rx.util.functions.Action1;

import com.netflix.client.netty.http.NettyHttpClient;
import com.netflix.client.netty.http.NettyHttpClientBuilder;

public class SimpleGet {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings
    public static void main(String[] args) throws Exception {
        NettyHttpClient client = NettyHttpClientBuilder.newBuilder().buildHttpClient();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/");
                
        final CountDownLatch latch = new CountDownLatch(1);
        client.submit("www.google.com", 80, request)
            .toBlockingObservable()
            .forEach(new Action1<HttpClientResponse<ByteBuf>>() {
                @Override
                public void call(HttpClientResponse<ByteBuf> t1) {
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
