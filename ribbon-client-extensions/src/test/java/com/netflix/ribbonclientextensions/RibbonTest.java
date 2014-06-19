package com.netflix.ribbonclientextensions;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import org.junit.Test;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.netty.RibbonTransport;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
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
    public void testCommandWithMetaData() throws IOException {
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
        Observable<String> result = request.withMetadata().toObservable().flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>(){
            @Override
            public Observable<String> call(
                    final RibbonResponse<Observable<ByteBuf>> response) {
                System.err.println(response.getHystrixInfo().isSuccessfulExecution());
                return response.content().map(new Func1<ByteBuf, String>(){
                    @Override
                    public String call(ByteBuf t1) {
                        System.err.println(response.getHystrixInfo().isSuccessfulExecution());
                        return t1.toString(Charset.defaultCharset());
                    }
                });
            }
        });
        String s = result.toBlocking().single();
        System.err.println(s);
    }

    
    @Test
    public void testTransformer() throws IOException, InterruptedException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
        
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(Lists.newArrayList(new Server("localhost", server.getPort())));
        HttpClient<ByteBuf, ByteBuf> httpClient = RibbonTransport.newHttpClient(lb, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("test", httpClient)
                .withNetworkResponseTransformer(new ResponseTransformer<HttpClientResponse<ByteBuf>>() {
                    @Override
                    public HttpClientResponse<ByteBuf> call(HttpClientResponse<ByteBuf> t1) {
                        throw new HystrixBadRequestException("error", new IllegalArgumentException());
                    }
                });
        RibbonRequest<ByteBuf> request = template.withUri("/").requestBuilder().build();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        request.toObservable().subscribe(new Action1<ByteBuf>() {
            @Override
            public void call(ByteBuf t1) {
            }
        }, 
        new Action1<Throwable>(){
            @Override
            public void call(Throwable t1) {
                error.set(t1);
                latch.countDown();
            }
        }, 
        new Action0() {

            @Override
            public void call() {
                // TODO Auto-generated method stub

            }
        });
        latch.await();
        assertTrue(error.get() instanceof HystrixBadRequestException);
        assertTrue(error.get().getCause() instanceof IllegalArgumentException);
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
        final AtomicReference<HystrixExecutableInfo<?>> hystrixInfo = new AtomicReference<HystrixExecutableInfo<?>>();
        Observable<String> result = request.withMetadata().toObservable().flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>(){
            @Override
            public Observable<String> call(
                    final RibbonResponse<Observable<ByteBuf>> response) {
                System.err.println(response.getHystrixInfo().isResponseFromFallback());
                return response.content().map(new Func1<ByteBuf, String>(){
                    @Override
                    public String call(ByteBuf t1) {
                        System.err.println(response.getHystrixInfo().isResponseFromFallback());
                        hystrixInfo.set(response.getHystrixInfo());
                        return t1.toString(Charset.defaultCharset());
                    }
                });
            }
        });
        String s = result.toBlocking().single();
        assertTrue(hystrixInfo.get().isResponseFromFallback());
        assertEquals(fallback, s);
    }
    
    @Test
    public void testCacheHit() {
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(Lists.newArrayList(new Server("localhost", 12345)));
        HttpClient<ByteBuf, ByteBuf> httpClient = RibbonTransport.newHttpClient(lb, 
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().setPropertyWithType(IClientConfigKey.CommonKeys.MaxAutoRetriesNextServer, 1));
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("test", httpClient);
        final String content = "from cache";
        final String cacheKey = "somekey";
        RibbonRequest<ByteBuf> request = template.withCacheKey(cacheKey)
                .addCacheProvider(new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key) {
                        return Observable.error(new Exception("Cache miss"));
                    }
                })
                .addCacheProvider(new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key) {
                        if (key.equals(cacheKey)) {
                            return Observable.just(Unpooled.buffer().writeBytes(content.getBytes()));
                        } else {
                            return Observable.error(new Exception("Cache miss"));
                        }
                    }
                })
                .withUri("/")
                .withHystrixProperties(HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("group"))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withRequestCacheEnabled(false))
                        )
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals(content, result);
    }
    
    @Test
    public void testCacheMiss() throws IOException {
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
        
        ILoadBalancer lb = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(Lists.newArrayList(new Server("localhost", server.getPort())));
        HttpClient<ByteBuf, ByteBuf> httpClient = RibbonTransport.newHttpClient(lb, 
                DefaultClientConfigImpl.getClientConfigWithDefaultValues().setPropertyWithType(IClientConfigKey.CommonKeys.MaxAutoRetriesNextServer, 1));
        HttpRequestTemplate<ByteBuf, ByteBuf> template = Ribbon.newHttpRequestTemplate("test", httpClient);
        final String cacheKey = "somekey";
        RibbonRequest<ByteBuf> request = template.withCacheKey(cacheKey)
                .addCacheProvider(new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key) {
                        return Observable.error(new Exception("Cache miss"));
                    }
                })
                .addCacheProvider(new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key) {
                        return Observable.error(new Exception("Cache miss again"));
                    }
                })
                .withUri("/")
                .withHystrixProperties(HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("group"))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withRequestCacheEnabled(false))
                        )
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals(content, result);
    }

}
