package com.netflix.niws.client.http;

import static org.junit.Assert.assertEquals;

import java.net.URI;

import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class FollowRedirectTest {
    @Test
    public void testRedirectNotFollowed() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues("myclient");
        config.setProperty(CommonClientConfigKey.FollowRedirects, Boolean.FALSE);
        ClientFactory.registerClientFromProperties("myclient", config);
        RestClient client = (RestClient) ClientFactory.getNamedClient("myclient");
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://jigsaw.w3.org/HTTP/300/302.html")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertEquals(302, response.getStatus());          
    }

    @Test
    public void testRedirectFollowed() throws Exception {
        DefaultClientConfigImpl config = DefaultClientConfigImpl.getClientConfigWithDefaultValues("myclient2");
        ClientFactory.registerClientFromProperties("myclient2", config);
        com.netflix.niws.client.http.RestClient client = (com.netflix.niws.client.http.RestClient) ClientFactory.getNamedClient("myclient2");
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://jigsaw.w3.org/HTTP/300/302.html")).build();
        HttpResponse response = client.execute(request);
        assertEquals(200, response.getStatus());      
    }

}
