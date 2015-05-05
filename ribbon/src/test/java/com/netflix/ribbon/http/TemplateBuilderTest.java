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
package com.netflix.ribbon.http;

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.hystrix.HystrixObservableCommandChain;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpRequestHeaders;
import org.junit.Test;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TemplateBuilderTest {
    
    private static class FakeCacheProvider implements CacheProvider<ByteBuf> {
        String id;
        
        FakeCacheProvider(String id) {
            this.id = id;
        }
        
        @Override
        public Observable<ByteBuf> get(final String key,
                Map<String, Object> requestProperties) {
            if (key.equals(id)) {
                return Observable.just(Unpooled.buffer().writeBytes(id.getBytes(Charset.defaultCharset())));

            } else {
                return Observable.error(new IllegalArgumentException());
            }
        };
    }        

    @Test
    public void testVarReplacement() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroupBuilder("test").build();
        
        HttpRequestTemplate<ByteBuf> template = group.newTemplateBuilder("testVarReplacement", ByteBuf.class)
                .withMethod("GET")
                .withUriTemplate("/foo/{id}?name={name}").build();
        HttpClientRequest<ByteBuf> request = template
                .requestBuilder()
                .withRequestProperty("id", "3")
                .withRequestProperty("name", "netflix")
                .createClientRequest();
        assertEquals("/foo/3?name=netflix", request.getUri());
    }
    
    @Test
    public void testCacheKeyTemplates() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroupBuilder("test").build();
        
        HttpRequestTemplate<ByteBuf> template = group.newTemplateBuilder("testCacheKeyTemplates", ByteBuf.class)
                .withUriTemplate("/foo/{id}")
                .withMethod("GET")
                .withCacheProvider("/cache/{id}", new FakeCacheProvider("/cache/5"))
                .build();
        
        RibbonRequest<ByteBuf> request = template.requestBuilder().withRequestProperty("id", 5).build();
        ByteBuf result = request.execute();
        assertEquals("/cache/5", result.toString(Charset.defaultCharset()));
    }
    
    @Test
    public void testHttpHeaders() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroupBuilder("test")
            .withHeader("header1", "group").build();
        
        HttpRequestTemplate<String> template = group.newTemplateBuilder("testHttpHeaders", String.class)
            .withUriTemplate("/foo/bar")
            .withMethod("GET")
            .withHeader("header2", "template")
            .withHeader("header1", "template").build();
        
        HttpRequestBuilder<String> requestBuilder = template.requestBuilder();
        requestBuilder.withHeader("header3", "builder").withHeader("header1", "builder");
		HttpClientRequest<ByteBuf> request = requestBuilder.createClientRequest();
        HttpRequestHeaders headers = request.getHeaders();
        List<String> header1 = headers.getAll("header1");
        assertEquals(3, header1.size());
        assertEquals("group", header1.get(0));
        assertEquals("template", header1.get(1));
        assertEquals("builder", header1.get(2));
        List<String> header2 = headers.getAll("header2");
        assertEquals(1, header2.size());
        assertEquals("template", header2.get(0));
        List<String> header3 = headers.getAll("header3");
        assertEquals(1, header3.size());
        assertEquals("builder", header3.get(0));
    }

    @Test
    public void testHystrixProperties() {
        ClientOptions clientOptions = ClientOptions.create()
                .withMaxAutoRetriesNextServer(1)
                .withMaxAutoRetries(1)
                .withConnectTimeout(1000)
                .withMaxTotalConnections(400)
                .withReadTimeout(2000);
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("test", clientOptions);
        HttpRequestTemplate<ByteBuf> template = group.newTemplateBuilder("testHystrixProperties", ByteBuf.class)
                .withMethod("GET")
                .withUriTemplate("/foo/bar").build();
        HttpRequest<ByteBuf> request = (HttpRequest<ByteBuf>) template
            .requestBuilder().build();
        HystrixObservableCommandChain<ByteBuf> hystrixCommandChain = request.createHystrixCommandChain();
        HystrixCommandProperties props = hystrixCommandChain.getCommands().get(0).getProperties();
        assertEquals(400, props.executionIsolationSemaphoreMaxConcurrentRequests().get().intValue());
        assertEquals(12000, props.executionIsolationThreadTimeoutInMilliseconds().get().intValue());
    }
}

