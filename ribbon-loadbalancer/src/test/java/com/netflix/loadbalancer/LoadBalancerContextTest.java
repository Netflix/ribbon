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
package com.netflix.loadbalancer;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.net.URLEncoder;

import org.junit.Test;

import com.netflix.client.config.IClientConfig;

public class LoadBalancerContextTest {
    
    final static Object httpKey = "http";
    final static Object httpsKey = "https";

    static BaseLoadBalancer lb = new BaseLoadBalancer() {

        @Override
        public Server chooseServer(Object key) {
            return new Server("www.example.com:8080");
        }
    };
    
    static BaseLoadBalancer mixedSchemeLb = new BaseLoadBalancer() {

        @Override
        public Server chooseServer(Object key) {
            if (key == httpKey) {
                return new Server("http://www.example.com:8081");
            } else if (key == httpsKey) {
                return new Server("https://www.example.com:8443");
            }
            
            return new Server("www.example.com:8080");
        }
    };
    
    
    private MyLoadBalancerContext context;
    
    public LoadBalancerContextTest() {
        context = new MyLoadBalancerContext(lb);
    }
    
    @Test
    public void testComputeURIWithMixedSchemaLoadBalancer() throws Exception {
        
        context = new MyLoadBalancerContext(mixedSchemeLb);
        
        URI request = new URI("/test?abc=xyz");
        
        // server with no scheme defined
        Server server = context.getServerFromLoadBalancer(request, null);
        URI newURI = context.reconstructURIWithServer(server, request);
        assertEquals("http://www.example.com:8080/test?abc=xyz", newURI.toString());
        

        // server with no scheme defined
        server = context.getServerFromLoadBalancer(request, httpKey);
        newURI = context.reconstructURIWithServer(server, request);
        assertEquals("http://www.example.com:8081/test?abc=xyz", newURI.toString());
        
        server = context.getServerFromLoadBalancer(request, httpsKey);
        newURI = context.reconstructURIWithServer(server, request);
        assertEquals("https://www.example.com:8443/test?abc=xyz", newURI.toString());
    }
    
    @Test
    public void testComputeFinalUriWithLoadBalancer() throws Exception {
        URI request = new URI("/test?abc=xyz");
        Server server = context.getServerFromLoadBalancer(request, null);
        URI newURI = context.reconstructURIWithServer(server, request);
        assertEquals("http://www.example.com:8080/test?abc=xyz", newURI.toString());
    }
    
    @Test
    public void testEncodedPath() throws Exception {
        String uri = "http://localhost:8080/resources/abc%2Fxyz";
        URI request = new URI(uri);
        Server server = context.getServerFromLoadBalancer(request, null);
        URI newURI = context.reconstructURIWithServer(server, request);
        assertEquals(uri, newURI.toString());
    }
    
    @Test
    public void testPreservesUserInfo() throws Exception {
        // %3A == ":" -- ensure user info is not decoded
        String uri = "http://us%3Aer:pass@localhost:8080?foo=bar";
        URI requestedURI = new URI(uri);
        Server server = context.getServerFromLoadBalancer(requestedURI, null);
        URI newURI = context.reconstructURIWithServer(server, requestedURI);
        assertEquals(uri, newURI.toString());
    }
    
    @Test
    public void testQueryWithoutPath() throws Exception {
        String uri = "?foo=bar";
        URI requestedURI = new URI(uri);
        Server server = context.getServerFromLoadBalancer(requestedURI, null);
        URI newURI = context.reconstructURIWithServer(server, requestedURI);
        assertEquals("http://www.example.com:8080?foo=bar", newURI.toString());
    }
    @Test
    public void testEncodedPathAndHostChange() throws Exception {
        String uri = "/abc%2Fxyz";
        URI request = new URI(uri);
        Server server = context.getServerFromLoadBalancer(request, null);
        URI newURI = context.reconstructURIWithServer(server, request);
        assertEquals("http://www.example.com:8080" + uri, newURI.toString());
    }

    
    @Test
    public void testEncodedQuery() throws Exception {
        String uri = "http://localhost:8080/resources/abc?";
        String queryString = "name=" + URLEncoder.encode("????&=*%!@#$%^&*()", "UTF-8");   
        URI request = new URI(uri + queryString);
        Server server = context.getServerFromLoadBalancer(request, null);
        URI newURI = context.reconstructURIWithServer(server, request);
        assertEquals(uri + queryString, newURI.toString());        
    }
}

class MyLoadBalancerContext extends LoadBalancerContext {

    public MyLoadBalancerContext(ILoadBalancer lb, IClientConfig clientConfig) {
        super(lb, clientConfig);
    }

    public MyLoadBalancerContext(ILoadBalancer lb) {
        super(lb);
    }
    
}
