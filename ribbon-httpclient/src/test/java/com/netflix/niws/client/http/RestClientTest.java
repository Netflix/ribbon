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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;


public class RestClientTest {
    @Test
    public void testExecuteWithoutLB() throws Exception {
        RestClient client = (RestClient) ClientFactory.getNamedClient("google");
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("http://www.google.com/")).build();
        HttpClientResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
        response = client.execute(request);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testExecuteWithLB() throws Exception {
        RestClient client = (RestClient) ClientFactory.getNamedClient("allservices");
        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 80), new Server("www.yahoo.com", 80), new Server("www.microsoft.com", 80)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);
        Set<URI> expected = new HashSet<URI>();
        expected.add(new URI("http://www.google.com:80/"));
        expected.add(new URI("http://www.microsoft.com:80/"));
        expected.add(new URI("http://www.yahoo.com:80/"));
        Set<URI> result = new HashSet<URI>();
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("/")).build();
        for (int i = 0; i < 5; i++) {
            HttpClientResponse response = client.executeWithLoadBalancer(request);
            assertEquals(200, response.getStatus());
            assertTrue(response.isSuccess());
            String content = response.getEntity(String.class);
            response.releaseResources();
            assertTrue(content.length() > 100);
            result.add(response.getRequestedURI());
        }
        assertEquals(expected, result);
        request = HttpClientRequest.newBuilder().setUri(new URI("http://www.linkedin.com/")).build();
        HttpClientResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testVipAsURI()  throws Exception {
    	ConfigurationManager.getConfigInstance().setProperty("test1.ribbon.DeploymentContextBasedVipAddresses", "google.com:80");
    	ConfigurationManager.getConfigInstance().setProperty("test1.ribbon.InitializeNFLoadBalancer", "false");
        RestClient client = (RestClient) ClientFactory.getNamedClient("test1");
        assertNull(client.getLoadBalancer());
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("/")).build();
        HttpClientResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
        assertEquals("http://google.com:80/", response.getRequestedURI().toString());
    }

    @Test
    public void testSecureClient()  throws Exception {
    	ConfigurationManager.getConfigInstance().setProperty("test2.ribbon.IsSecure", "true");
    	RestClient client = (RestClient) ClientFactory.getNamedClient("test2");
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("https://www.google.com/")).build();
        HttpClientResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testSecureClient2()  throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("test3.ribbon.IsSecure", "true");
        RestClient client = (RestClient) ClientFactory.getNamedClient("test3");
        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 443)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("/")).build();
        HttpClientResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
        assertEquals("https://www.google.com:443/", response.getRequestedURI().toString());

    }


}
