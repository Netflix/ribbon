package com.netflix.ribbonclientextensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

import com.google.common.collect.Lists;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
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
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", 
                ClientOptions.create()
                .withMaxAutoRetriesNextServer(3)
                .useConfigurationBasedServerList("localhost:12345, localhost:10092, localhost:" + server.getPort()));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/").requestBuilder().build();
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
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .useConfigurationBasedServerList("localhost:" + server.getPort())
                .withMaxAutoRetriesNextServer(3));
        
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test");
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/")
                .addCacheProvider("somekey", new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        return Observable.error(new Exception("Cache miss"));
                    }
                }).withHystrixProperties(HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("group"))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withRequestCacheEnabled(false))
                        )
                .requestBuilder().build();
        final AtomicBoolean success = new AtomicBoolean(false);
        Observable<String> result = request.withMetadata().toObservable().flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>(){
            @Override
            public Observable<String> call(
                    final RibbonResponse<Observable<ByteBuf>> response) {
                success.set(response.getHystrixInfo().isSuccessfulExecution());
                return response.content().map(new Func1<ByteBuf, String>(){
                    @Override
                    public String call(ByteBuf t1) {
                        return t1.toString(Charset.defaultCharset());
                    }
                });
            }
        });
        String s = result.toBlocking().single();
        assertEquals(content, s);
        assertTrue(success.get());
    }

   
    @Test
    public void testTransformer() throws IOException, InterruptedException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .useConfigurationBasedServerList("localhost:" + server.getPort()));
        
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);

                template.withResponseValidator(new ResponseValidator<HttpClientResponse<ByteBuf>>() {
                    @Override
                    public void validate(HttpClientResponse<ByteBuf> t1) throws UnsuccessfulResponseException {
                        throw new UnsuccessfulResponseException("error", new IllegalArgumentException());
                    }
                });
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/").requestBuilder().build();
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
            }
        });
        latch.await();
        assertTrue(error.get() instanceof HystrixBadRequestException);
        assertTrue(error.get().getCause() instanceof UnsuccessfulResponseException);
    }

    @Test
    public void testFallback() throws IOException {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .useConfigurationBasedServerList("localhost:12345")
                .withMaxAutoRetriesNextServer(1));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);
        final String fallback = "fallback";
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/")
                .withFallbackProvider(new FallbackHandler<ByteBuf>() {
                    @Override
                    public Observable<ByteBuf> getFallback(
                            HystrixExecutableInfo<?> hystrixInfo,
                            Map<String, Object> requestProperties) {
                        try {
                            return Observable.just(Unpooled.buffer().writeBytes(fallback.getBytes("UTF-8")));
                        } catch (UnsupportedEncodingException e) {
                            return Observable.error(e);
                        }
                    }
                })
                .requestBuilder().build();
        final AtomicReference<HystrixExecutableInfo<?>> hystrixInfo = new AtomicReference<HystrixExecutableInfo<?>>();
        final AtomicBoolean failed = new AtomicBoolean(false);
        Observable<String> result = request.withMetadata().toObservable().flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>(){
            @Override
            public Observable<String> call(
                    final RibbonResponse<Observable<ByteBuf>> response) {
                hystrixInfo.set(response.getHystrixInfo());
                failed.set(response.getHystrixInfo().isFailedExecution());
                return response.content().map(new Func1<ByteBuf, String>(){
                    @Override
                    public String call(ByteBuf t1) {
                        return t1.toString(Charset.defaultCharset());
                    }
                });
            }
        });
        String s = result.toBlocking().single();
        // this returns true only after the blocking call is done
        assertTrue(hystrixInfo.get().isResponseFromFallback());
        assertTrue(failed.get());
        assertEquals(fallback, s);
    }
    
    @Test
    public void testCacheHit() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .useConfigurationBasedServerList("localhost:12345")
                .withMaxAutoRetriesNextServer(1));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test");
        final String content = "from cache";
        final String cacheKey = "somekey";
        RibbonRequest<ByteBuf> request = template.addCacheProvider(cacheKey, new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        return Observable.error(new Exception("Cache miss"));
                    }
                })
                .addCacheProvider(cacheKey, new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        if (key.equals(cacheKey)) {
                            try {
                                return Observable.just(Unpooled.buffer().writeBytes(content.getBytes("UTF-8")));
                            } catch (UnsupportedEncodingException e) {
                                return Observable.error(e);                            
                            }
                        } else {
                            return Observable.error(new Exception("Cache miss"));
                        }
                    }
                })
                .withUriTemplate("/")
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
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .useConfigurationBasedServerList("localhost:" + server.getPort())
                .withMaxAutoRetriesNextServer(1));

        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test");
        final String cacheKey = "somekey";
        RibbonRequest<ByteBuf> request = template.addCacheProvider(cacheKey, new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        return Observable.error(new Exception("Cache miss"));
                    }
                })
                .addCacheProvider(cacheKey, new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        return Observable.error(new Exception("Cache miss again"));
                    }
                })
                .withUriTemplate("/")
                .withHystrixProperties(HystrixObservableCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey("group"))
                        .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withRequestCacheEnabled(false))
                        )
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals(content, result);
    } 
}
