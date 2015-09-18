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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.netflix.client.ClientException;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.testutil.MockHttpServer;
import com.netflix.config.ConfigurationManager;
import com.netflix.http4.MonitoredConnectionManager;
import com.netflix.http4.NFHttpClient;
import com.netflix.http4.NFHttpClientFactory;
import com.netflix.loadbalancer.AvailabilityFilteringRule;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;

public class RetryTest {
    @ClassRule
    public static MockHttpServer server = new MockHttpServer()
    ;

    private RestClient client;
    private BaseLoadBalancer lb;
    private NFHttpClient httpClient; 
    private MonitoredConnectionManager connectionPoolManager; 
    private static Server localServer;
    
    @BeforeClass 
    public static void init() throws Exception {
//        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.niws.client.http");
        localServer = new Server("localhost", server.getServerPort());
    }
    
    @Before
    public void beforeTest() {
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
        URI localUrl = new URI("/status?code=503");
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
        URI localUrl = new URI("/status?code=503");
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).build();
        try {
            client.executeWithLoadBalancer(request, 
                    DefaultClientConfigImpl
                    .getEmptyConfig()
                    .set(CommonClientConfigKey.MaxAutoRetries, 1)
                    .set(CommonClientConfigKey.MaxAutoRetriesNextServer, 0));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        assertEquals(2, lb.getLoadBalancerStats().getSingleServerStat(localServer).getSuccessiveConnectionFailureCount());        
    }
    
    @Test
    public void testThrottledWithRetryNextServer() throws Exception {
        int connectionCount = connectionPoolManager.getConnectionsInPool();
        URI localUrl = new URI("/status?code=503");
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
        URI localUrl = new URI("/noresponse");
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
        URI localUrl = new URI("/noresponse");
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
        URI localUrl = new URI("/noresponse");
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
        URI localUrl = new URI("/noresponse");
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
        URI localUrl = new URI("/status?code=503");
        lb.setServersList(Lists.newArrayList(localServer));
        HttpRequest request = HttpRequest.newBuilder().uri(localUrl).verb(Verb.POST).setRetriable(true).build();
        try {
            HttpResponse response = client.executeWithLoadBalancer(request, DefaultClientConfigImpl.getEmptyConfig().set(CommonClientConfigKey.MaxAutoRetriesNextServer, 2));
            fail("Exception expected");
        } catch (ClientException e) { // NOPMD
        }
        ServerStats stats = lb.getLoadBalancerStats().getSingleServerStat(localServer); 
        assertEquals(3, stats.getSuccessiveConnectionFailureCount());
    }

    
    @Test
    public void testSuccessfulRetries() throws Exception {
        lb.setServersList(Lists.newArrayList(new Server("localhost:12987"), new Server("localhost:12987"), localServer));
        URI localUrl = new URI("/ok");
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
