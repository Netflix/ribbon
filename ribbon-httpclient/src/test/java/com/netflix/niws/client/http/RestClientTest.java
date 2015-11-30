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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.ClassRule;
import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.testutil.MockHttpServer;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;


public class RestClientTest {
    @ClassRule
    public static MockHttpServer server = new MockHttpServer();
    
    @ClassRule
    public static MockHttpServer secureServer = new MockHttpServer().secure();
    
    @Test
    public void testExecuteWithoutLB() throws Exception {
        RestClient client = (RestClient) ClientFactory.getNamedClient("google");
        HttpRequest request = HttpRequest.newBuilder().uri(server.getServerURI()).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertStatusIsOk(response.getStatus());
        response = client.execute(request);
        assertStatusIsOk(response.getStatus());
    }

    @Test
    public void testExecuteWithLB() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("allservices.ribbon." + CommonClientConfigKey.ReadTimeout, "10000");
        ConfigurationManager.getConfigInstance().setProperty("allservices.ribbon." + CommonClientConfigKey.FollowRedirects, "true");
        RestClient client = (RestClient) ClientFactory.getNamedClient("allservices");
        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("localhost", server.getServerPort())};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);
        Set<URI> expected = new HashSet<URI>();
        expected.add(new URI(server.getServerPath("/")));
        Set<URI> result = new HashSet<URI>();
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        for (int i = 0; i < 5; i++) {
            HttpResponse response = client.executeWithLoadBalancer(request);
            assertStatusIsOk(response.getStatus());
            assertTrue(response.isSuccess());
            String content = response.getEntity(String.class);
            response.close();
            assertFalse(content.isEmpty());
            result.add(response.getRequestedURI());
        }
        assertEquals(expected, result);
        request = HttpRequest.newBuilder().uri(server.getServerURI()).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testVipAsURI()  throws Exception {
    	ConfigurationManager.getConfigInstance().setProperty("test1.ribbon.DeploymentContextBasedVipAddresses", server.getServerPath("/"));
    	ConfigurationManager.getConfigInstance().setProperty("test1.ribbon.InitializeNFLoadBalancer", "false");
        RestClient client = (RestClient) ClientFactory.getNamedClient("test1");
        assertNull(client.getLoadBalancer());
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertStatusIsOk(response.getStatus());
        assertEquals(server.getServerPath("/"), response.getRequestedURI().toString());
    }

    @Test
    public void testSecureClient()  throws Exception {
    	ConfigurationManager.getConfigInstance().setProperty("test2.ribbon.IsSecure", "true");
    	RestClient client = (RestClient) ClientFactory.getNamedClient("test2");
        HttpRequest request = HttpRequest.newBuilder().uri(server.getServerURI()).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertStatusIsOk(response.getStatus());
    }

    @Test
    public void testSecureClient2()  throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("test3.ribbon." + CommonClientConfigKey.IsSecure, "true");
        ConfigurationManager.getConfigInstance().setProperty("test3.ribbon." + CommonClientConfigKey.TrustStore, secureServer.getTrustStore().getAbsolutePath());
        ConfigurationManager.getConfigInstance().setProperty("test3.ribbon." + CommonClientConfigKey.TrustStorePassword, SecureGetTest.PASSWORD);
        
        RestClient client = (RestClient) ClientFactory.getNamedClient("test3");
        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("localhost", secureServer.getServerPort())}; 
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertStatusIsOk(response.getStatus());
        assertEquals(secureServer.getServerPath("/"), response.getRequestedURI().toString());
        
    }

    @Test
    public void testDelete() throws Exception {
        RestClient client = (RestClient) ClientFactory.getNamedClient("google");
        HttpRequest request = HttpRequest.newBuilder().uri(server.getServerURI()).verb(HttpRequest.Verb.DELETE).build();
        HttpResponse response = client.execute(request);
        assertStatusIsOk(response.getStatus());
        
        request = HttpRequest.newBuilder().uri(server.getServerURI()).verb(HttpRequest.Verb.DELETE).entity("").build();
        response = client.execute(request);
        assertStatusIsOk(response.getStatus());
    }

    private void assertStatusIsOk(int status) {
        assertTrue(status == 200 || status == 302);
    }
}
