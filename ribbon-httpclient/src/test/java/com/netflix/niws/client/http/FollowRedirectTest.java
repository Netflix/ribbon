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
