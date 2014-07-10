/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.Test;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.hystrix.FallbackHandler;

public class RibbonTest {
    
    private static String toStringBlocking(RibbonRequest<ByteBuf> request) {
        return request.toObservable().map(new Func1<ByteBuf, String>() {
            @Override
            public String call(ByteBuf t1) {
                return t1.toString(Charset.defaultCharset());
            }
            
        }).toBlocking().single();
    }
    
    
    @Test
    public void testCommand() throws IOException, InterruptedException, ExecutionException {
        // LogManager.getRootLogger().setLevel(Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        MockResponse response = new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
        .setBody(content);
        server.enqueue(response);        
        server.enqueue(response);       
        server.enqueue(response);       
        server.play();
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", 
                ClientOptions.create()
                .withMaxAutoRetriesNextServer(3)
                .withReadTimeout(300000)
                .withConfigurationBasedServerList("localhost:12345, localhost:10092, localhost:" + server.getPort()));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);
        RibbonRequest<ByteBuf> request = template
                .withUriTemplate("/")
                .withMethod("GET")
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals(content, result);
        // repeat the same request
        ByteBuf raw = request.execute();
        result = raw.toString(Charset.defaultCharset());
        raw.release();
        assertEquals(content, result);
        
        result = request.queue().get().toString(Charset.defaultCharset());
        assertEquals(content, result);
    }
    
    @Test
    public void testHystrixCache() throws IOException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        MockResponse response = new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
        .setBody(content);
        server.enqueue(response);
        
        server.enqueue(response);       
        server.play();
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient");
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);
        RibbonRequest<ByteBuf> request = template
                .withUriTemplate("http://localhost:" + server.getPort())
                .withMethod("GET")
                .withRequestCacheKey("xyz")
                .requestBuilder().build(); 
        HystrixRequestContext context = HystrixRequestContext.initializeContext();
        try {
            RibbonResponse<ByteBuf> ribbonResponse = request.withMetadata().execute();
            assertFalse(ribbonResponse.getHystrixInfo().isResponseFromCache());
            ribbonResponse = request.withMetadata().execute();
            assertTrue(ribbonResponse.getHystrixInfo().isResponseFromCache());
        } finally {
            context.shutdown();
        }
    }

    
    
    @Test
    public void testCommandWithMetaData() throws IOException, InterruptedException, ExecutionException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       

        server.play();
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .withConfigurationBasedServerList("localhost:" + server.getPort())
                .withMaxAutoRetriesNextServer(3));
        
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test");
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/")
                .withMethod("GET")
                .withCacheProvider("somekey", new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        return Observable.error(new Exception("Cache miss"));
                    }
                })
                .requestBuilder().build();
        final AtomicBoolean success = new AtomicBoolean(false);
        RequestWithMetaData<ByteBuf> metaRequest = request.withMetadata();
        Observable<String> result = metaRequest.toObservable().flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<String>>(){
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
        
        Future<RibbonResponse<ByteBuf>> future = metaRequest.queue();
        RibbonResponse<ByteBuf> response = future.get();
        assertTrue(future.isDone());
        assertEquals(content, response.content().toString(Charset.defaultCharset()));
        assertTrue(response.getHystrixInfo().isSuccessfulExecution()); 
        
        RibbonResponse<ByteBuf> result1 = metaRequest.execute();
        assertEquals(content, result1.content().toString(Charset.defaultCharset()));
        assertTrue(result1.getHystrixInfo().isSuccessfulExecution());
    }

   
    @Test
    public void testValidator() throws IOException, InterruptedException {
        // LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
        
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .withConfigurationBasedServerList("localhost:" + server.getPort()));
        
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);

                template.withResponseValidator(new ResponseValidator<HttpClientResponse<ByteBuf>>() {
                    @Override
                    public void validate(HttpClientResponse<ByteBuf> t1) throws UnsuccessfulResponseException {
                        throw new UnsuccessfulResponseException("error", new IllegalArgumentException());
                    }
                });
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/")
                .withMethod("GET")
                .requestBuilder().build();
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
                .withConfigurationBasedServerList("localhost:12345")
                .withMaxAutoRetriesNextServer(1));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);
        final String fallback = "fallback";
        RibbonRequest<ByteBuf> request = template.withUriTemplate("/")
                .withMethod("GET")
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
                .withConfigurationBasedServerList("localhost:12345")
                .withMaxAutoRetriesNextServer(1));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test");
        final String content = "from cache";
        final String cacheKey = "somekey";
        RibbonRequest<ByteBuf> request = template
                .withCacheProvider(cacheKey, new CacheProvider<ByteBuf>(){
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
                .withMethod("GET")
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals(content, result);
    }
    
    @Test
    public void testObserve() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", 
                ClientOptions.create()
                .withMaxAutoRetriesNextServer(3)
                .withReadTimeout(300000)
                .withConfigurationBasedServerList("localhost:12345, localhost:10092, localhost:" + server.getPort()));
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test", ByteBuf.class);
        RibbonRequest<ByteBuf> request = template
                .withUriTemplate("/")
                .withMethod("GET")
                .requestBuilder().build();
        Observable<ByteBuf> result = request.observe();
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] fromCommand = new String[]{""};
        Thread.sleep(2000);
        result.subscribe(new Action1<ByteBuf>() {
            @Override
            public void call(ByteBuf t1) {
                try {
                    fromCommand[0] = t1.toString(Charset.defaultCharset());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                latch.countDown();
            }
        });
        latch.await();
        assertEquals(content, fromCommand[0]);
        
        Observable<RibbonResponse<Observable<ByteBuf>>> metaResult = request.withMetadata().observe();
        fromCommand[0] = "";
        Thread.sleep(2000);
        fromCommand[0] = metaResult.flatMap(new Func1<RibbonResponse<Observable<ByteBuf>>, Observable<ByteBuf>>(){
            @Override
            public Observable<ByteBuf> call(
                    RibbonResponse<Observable<ByteBuf>> t1) {
                return t1.content();
            }
        }).map(new Func1<ByteBuf, String>(){
            @Override
            public String call(ByteBuf t1) {
                return t1.toString(Charset.defaultCharset());
            }
        }).toBlocking().single();
        assertEquals(content, fromCommand[0]);
    }
    
    @Test
    public void testCacheMiss() throws IOException, InterruptedException {
        MockWebServer server = new MockWebServer();
        String content = "Hello world";
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content));       
        server.play();
                
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("myclient", ClientOptions.create()
                .withConfigurationBasedServerList("localhost:" + server.getPort())
                .withMaxAutoRetriesNextServer(1));

        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("test");
        final String cacheKey = "somekey";
        RibbonRequest<ByteBuf> request = template
                .withCacheProvider(cacheKey, new CacheProvider<ByteBuf>(){
                    @Override
                    public Observable<ByteBuf> get(String key, Map<String, Object> vars) {
                        return Observable.error(new Exception("Cache miss again"));
                    }
                })
                .withMethod("GET")
                .withUriTemplate("/")
                .requestBuilder().build();
        String result = toStringBlocking(request);
        assertEquals(content, result);
    } 
}
