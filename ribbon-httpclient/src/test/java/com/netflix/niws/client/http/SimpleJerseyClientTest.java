package com.netflix.niws.client.http;


import static org.junit.Assert.*;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.loadbalancer.NFLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.ClientFactory;
import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.niws.client.http.HttpClientRequest;
import com.netflix.niws.client.http.SimpleJerseyClient;


public class SimpleJerseyClientTest {    
    @Test
    public void testExecuteWithoutLB() throws Exception {
        SimpleJerseyClient client = (SimpleJerseyClient) ClientFactory.getNamedClient("google");
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("http://www.google.com/")).build();
        HttpClientResponse response = client.executeWithLoadBalancer(request);
        assertEquals(200, response.getStatus());
        response = client.execute(request);
        assertEquals(200, response.getStatus());
    }
    
    @Test
    public void testExecuteWithLB() throws Exception {
        SimpleJerseyClient client = (SimpleJerseyClient) ClientFactory.getNamedClient("allservices");
        NFLoadBalancer lb = new NFLoadBalancer();
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
    }
}
