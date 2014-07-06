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
package com.netflix.niws.client.http;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;
import com.netflix.config.ConfigurationManager;
import com.netflix.http4.MonitoredConnectionManager;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientFactory;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class RetryTest {
    private static HttpServer server = null;
    private static String SERVICE_URI;
    private static RestClient client;
    private static BaseLoadBalancer lb;
    private static int port = (new Random()).nextInt(1000) + 4000; 
    private static Server localServer = new Server("localhost", port);
    private static NFHttpClient httpClient; 
    private static MonitoredConnectionManager connectionPoolManager; 
    @BeforeClass 
    public static void init() throws Exception {
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.niws.client.http");
        SERVICE_URI = "http://localhost:" + port + "/";
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        
        ConfigurationManager.getConfigInstance().setProperty("RetryTest.ribbon.NFLoadBalancerClassName", BaseLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("RetryTest.ribbon.client.NFLoadBalancerPingClassName", DummyPing.class.getName());

        ConfigurationManager.getConfigInstance().setProperty("RetryTest.ribbon.ReadTimeout", "1000");
        ConfigurationManager.getConfigInstance().setProperty("RetryTest.ribbon." + CommonClientConfigKey.ConnectTimeout, "500");
        ConfigurationManager.getConfigInstance().setProperty("RetryTest.ribbon." + CommonClientConfigKey.OkToRetryOnAllOperations, "true");

        client = (RestClient) ClientFactory.getNamedClient("RetryTest");

        lb = (BaseLoadBalancer) client.getLoadBalancer();
        lb.setServersList(Lists.newArrayList(localServer));
        
        httpClient = NFHttpClientFactory.getNamedNFHttpClient("RetryTest");
        connectionPoolManager = (MonitoredConnectionManager) httpClient.getConnectionManager(); 

    }
    
    @AfterClass
    public static void shutDown() {
        server.stop(0);
    }

    @Before
    public void setUp() {
        client.setMaxAutoRetries(0);
        client.setMaxAutoRetriesNextServer(0);
        client.setOkToRetryOnAllOperations(false);
        lb.setServersList(Lists.newArrayList(localServer));
        // reset the server index
        lb.setRule(new AvailabilityFilteringRule());
        lb.getLoadBalancerStats().getSingleServerStat(localServer).clearSuccessiveConnectionFailureCount();
    }
    
    @Test
    public void testThrottled() throws Exception {
        URI localUrl = new URI("/test/get503");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 0));
            fail("Exception expected");
        } catch (ClientException e) {
            assertNotNull(e);
        }
        assertEquals(1, lb.getLoadBalancerStats().getSingleServerStat(localServer).getSuccessiveConnectionFailureCount());        
    }
    
    @Test
    public void testThrottledWithRetrySameServer() throws Exception {
        URI localUrl = new URI("/test/get503");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).build();
        try {
            client.executeWithLoadBalancer(request, 
                    DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetries, 1)
                    .set(CommonClientConfigKey.MaxAutoRetriesNextServer, 0));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        assertEquals(2, lb.getLoadBalancerStats().getSingleServerStat(localServer).getSuccessiveConnectionFailureCount());        
    }
    
    @Test
    public void testThrottledWithRetryNextServer() throws Exception {
        int connectionCount = connectionPoolManager.getConnectionsInPool();
        URI localUrl = new URI("/test/get503");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        assertEquals(3, lb.getLoadBalancerStats().getSingleServerStat(localServer).getSuccessiveConnectionFailureCount());
        System.out.println("Initial connections count " + connectionCount);
        System.out.println("Final connections count " + connectionPoolManager.getConnectionsInPool());
        // should be no connection leak        
        assertTrue(connectionPoolManager.getConnectionsInPool() <= connectionCount + 1);
    }
      
    @Test
    public void testReadTimeout() throws Exception {
        URI localUrl = new URI("/test/getReadtimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        assertEquals(3, lb.getLoadBalancerStats().getSingleServerStat(localServer).getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testReadTimeoutWithRetriesNextServe() throws Exception {
        URI localUrl = new URI("/test/getReadtimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        assertEquals(3, lb.getLoadBalancerStats().getSingleServerStat(localServer).getSuccessiveConnectionFailureCount());
    }

    @Test
    public void postReadTimeout() throws Exception {
        URI localUrl = new URI("/test/postReadtimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).verb(Verb.POST).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(localServer); 
        assertEquals(1, stats.getSuccessiveConnectionFailureCount());
    }
    
    
    @Test
    public void testRetriesOnPost() throws Exception {
        URI localUrl = new URI("/test/postReadtimeout");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).verb(Verb.POST).setRetriable(true).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(localServer); 
        assertEquals(3, stats.getSuccessiveConnectionFailureCount());
    }
    
    @Test
    public void testRetriesOnPostWithConnectException() throws Exception {
        URI localUrl = new URI("/test");
        lb.setServersList(Lists.newArrayList(new Server("www.google.com:81")));
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).verb(Verb.POST).setRetriable(true).build();
        try {
            client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(new Server("www.google.com:81")); 
        assertEquals(3, stats.getSuccessiveConnectionFailureCount());
    }

    
    @Test
    public void testSuccessfulRetries() throws Exception {
        lb.setServersList(Lists.newArrayList(new Server("localhost:12987"), new Server("localhost:12987"), localServer));
        URI localUrl = new URI("/test/getObject");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).queryParams("name", "ribbon").build();
        try {
            HttpResponse response = client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            assertEquals(200, response.getStatus());
        } catch (ClientException e) { 
            fail("Unexpected exception");
        }
        ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(new Server("localhost:12987")); 
        assertEquals(1, stats.getSuccessiveConnectionFailureCount());
    }
}
