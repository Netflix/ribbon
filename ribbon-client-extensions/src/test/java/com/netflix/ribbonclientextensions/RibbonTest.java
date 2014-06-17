package com.netflix.ribbonclientextensions;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClient;

import org.junit.Test;

import rx.Observable;

import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

public class RibbonTest {
    
    @Test
    public void testCommand() throws IOException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
        
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(Lists.newArrayList(new Server("localhost", server.getPort())));
        HttpClient<ByteBuf, ByteBuf> httpClient = RibbonTransport.newHttpClient(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("test", httpClient);
        RibbonRequest<ByteBuf> request = template.withUri("/").requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals(content, result);
    }
    
    @Test
    public void testFallback() throws IOException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(Lists.newArrayList(new Server("localhost", 12345)));
        HttpClient<ByteBuf, ByteBuf> httpClient = RibbonTransport.newHttpClient(lb, 
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().setPropertyWithType(IClientConfigKey.CommonKeys.MaxAutoRetriesNextServer, 1));
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("test", httpClient);
        final String fallback = "fallback";
        RibbonRequest<ByteBuf> request = template.withUri("/")
                .withFallbackProvider(new FallbackHandler<ByteBuf>() {
                    @Override
                    public Observable<ByteBuf> call(HystrixObservableCommand<ByteBuf> t1) {
                        return Observable.just(Unpooled.buffer().writeBytes(fallback.getBytes()));
                    }
                })
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        // System.out.println(result);
        assertEquals(fallback, result);
    }

}
