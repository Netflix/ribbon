package com.netflix.ribbonclientextensions.http;

import static org.junit.Assert.assertEquals;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpRequestHeaders;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import rx.Observable;

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.HystrixObservableCommand.Setter;
import com.netflix.ribbonclientextensions.CacheProvider;
import com.netflix.ribbonclientextensions.ClientOptions;
import com.netflix.ribbonclientextensions.Ribbon;
import com.netflix.ribbonclientextensions.RibbonRequest;

public class TemplateBuilderTest {
    
    private static class FakeCacheProvider implements CacheProvider<String> {
        String id;
        
        FakeCacheProvider(String id) {
            this.id = id;
        }
        
        @Override
        public Observable<String> get(final String key,
                Map<String, Object> requestProperties) {
            if (key.equals(id)) {
                return Observable.just(id);

            } else {
                return Observable.error(new IllegalArgumentException());
            }
        };
    }        

    @Test
    public void testVarReplacement() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("test");
        
        HttpRequestTemplate<ByteBuf> template = group.newRequestTemplate("testVarReplacement", ByteBuf.class);
        template.withUriTemplate("/foo/{id}?name={name}");
        HttpClientRequest<ByteBuf> request = template
                .requestBuilder()
                .withRequestProperty("id", "3")
                .withRequestProperty("name", "netflix")
                .createClientRequest();
        assertEquals("/foo/3?name=netflix", request.getUri());
    }
    
    @Test
    public void testCacheKeyTemplates() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("test");
        
        HttpRequestTemplate<String> template = group.newRequestTemplate("testCacheKeyTemplates", String.class);
        template.withUriTemplate("/foo/{id}")
            .addCacheProvider("cache.{id}", new FakeCacheProvider("cache.3"))
            .addCacheProvider("/cache/{id}", new FakeCacheProvider("/cache/5"));
        RibbonRequest<String> request = template.requestBuilder().withRequestProperty("id", 3).build();
        String result = request.execute();
        assertEquals("cache.3", result); 
        
        request = template.requestBuilder().withRequestProperty("id", 5).build();
        result = request.execute();
        assertEquals("/cache/5", result);
    }
    
    @Test
    public void testHttpHeaders() {
        HttpResourceGroup group = Ribbon.createHttpResourceGroup("test");
        group.withCommonHeader("header1", "group");
        
        HttpRequestTemplate<String> template = group.newRequestTemplate("testHttpHeaders", String.class);
        template.withUriTemplate("/foo/bar")
            .withHeader("header2", "template")
            .withHeader("header1", "template");
        
        HttpClientRequest<ByteBuf> request = template.requestBuilder().createClientRequest();
        HttpRequestHeaders headers = request.getHeaders();
        List<String> header1 = headers.getAll("header1");
        assertEquals(2, header1.size());
        assertEquals("group", header1.get(0));
        assertEquals("template", header1.get(1));
        List<String> header2 = headers.getAll("header2");
        assertEquals(1, header2.size());
        assertEquals("template", header2.get(0));
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
        HttpRequestTemplate<String> template = group.newRequestTemplate("testHystrixProperties", String.class);
        HttpRequest<String> request = (HttpRequest<String>) template.withMethod("GET")
            .withUriTemplate("/foo/bar")
            .requestBuilder().build();
        HystrixObservableCommand<String> command = request.createHystrixCommand();
        HystrixCommandProperties props = command.getProperties();
        assertEquals(400, props.executionIsolationSemaphoreMaxConcurrentRequests().get().intValue());
        assertEquals(12000, props.executionIsolationThreadTimeoutInMilliseconds().get().intValue());
    }
}

