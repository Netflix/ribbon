package com.netflix.ribbon.examples.netty.http;

import java.util.concurrent.CountDownLatch;

import rx.Observer;

import com.google.common.collect.Lists;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.netty.http.NettyHttpClientBuilder.NettyHttpLoadBalancingClientBuilder;
import com.netflix.client.netty.http.NettyHttpLoadBalancingClient;
import com.netflix.loadbalancer.AbstractLoadBalancer;
import com.netflix.loadbalancer.Server;

public class LoadBalancingExample {
    public static void main(String[] args) throws Exception {
        NettyHttpLoadBalancingClient client = NettyHttpLoadBalancingClientBuilder.newBuilder()
                .withFixedServerList(Lists.newArrayList(new Server("www.google.com:80"), new Server("www.microsoft.com:80"), new Server("www.yahoo.com:80")))
                .build();
        HttpRequest request = HttpRequest.newBuilder().uri("/").build();
        final CountDownLatch latch = new CountDownLatch(3); 
        Observer<HttpResponse> observer = new Observer<HttpResponse>() {

            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(HttpResponse args) {
                System.out.println("Got response from: " + args.getRequestedURI());                
            }
        };
        for (int i = 0; i < 3; i++) {
            client.observeHttpResponse(request, observer);
        }
        latch.await();        
        System.out.println(((AbstractLoadBalancer) client.getLoadBalancer()).getLoadBalancerStats());
    }
}
