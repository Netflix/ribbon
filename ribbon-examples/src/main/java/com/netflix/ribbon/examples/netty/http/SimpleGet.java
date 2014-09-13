package com.netflix.ribbon.examples.netty.http;

import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.functions.Action1;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimpleGet {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings
    public static void main(String[] args) throws Exception {
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("http://www.google.com/");
        final CountDownLatch latch = new CountDownLatch(1);
        client.submit(request)
            .toBlocking()
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
