/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.client;

import static org.junit.Assert.*;

import java.net.URLEncoder;

import org.junit.Test;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;

public class LoadBalancerContextTest {

    static BaseLoadBalancer lb = new BaseLoadBalancer() {

        @Override
        public Server chooseServer(Object key) {
            return new Server("www.example.com:8080");
        }
    };
    
    
    private MyLoadBalancerContext context;
    
    public LoadBalancerContextTest() {
        context = new MyLoadBalancerContext();
        context.setLoadBalancer(lb);
    }
    
    @Test
    public void testComputeFinalUriWithLoadBalancer() throws ClientException {
        HttpRequest request = HttpRequest.newBuilder().uri("/test?abc=xyz").build();
        HttpRequest newRequest = context.computeFinalUriWithLoadBalancer(request);
        assertEquals("http://www.example.com:8080/test?abc=xyz", newRequest.getUri().toString());
    }
    
    @Test
    public void testEncodedPath() throws ClientException {
        String uri = "http://localhost:8080/resources/abc%2Fxyz";
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpRequest newRequest = context.computeFinalUriWithLoadBalancer(request);
        assertEquals(uri, newRequest.getUri().toString());
    }
    
    @Test
    public void testPreservesUserInfo() throws ClientException {
        // %3A == ":" -- ensure user info is not decoded
        String uri = "http://us%3Aer:pass@localhost:8080?foo=bar";
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpRequest newRequest = context.computeFinalUriWithLoadBalancer(request);
        assertEquals(uri, newRequest.getUri().toString());
    }
    
    @Test
    public void testQueryWithoutPath() throws ClientException {
        String uri = "?foo=bar";
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpRequest newRequest = context.computeFinalUriWithLoadBalancer(request);
        assertEquals("http://www.example.com:8080?foo=bar", newRequest.getUri().toString());
    }
    
    @Test
    public void testEncodedPathAndHostChange() throws ClientException {
        String uri = "/abc%2Fxyz";
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpRequest newRequest = context.computeFinalUriWithLoadBalancer(request);
        assertEquals("http://www.example.com:8080" + uri, newRequest.getUri().toString());
    }

    
    @Test
    public void testEncodedQuery() throws Exception {
        String uri = "http://localhost:8080/resources/abc?";
        String queryString = "name=" + URLEncoder.encode("éƎ&=*%!@#$%^&*()", "UTF-8");   
        HttpRequest request = HttpRequest.newBuilder().uri(uri + queryString).build();
        HttpRequest newRequest = context.computeFinalUriWithLoadBalancer(request);
        assertEquals(uri + queryString, newRequest.getUri().toString());        
    }
}

class MyLoadBalancerContext extends LoadBalancerContext<HttpRequest, HttpResponse> {
}
