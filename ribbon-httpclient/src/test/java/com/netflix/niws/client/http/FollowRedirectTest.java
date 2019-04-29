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

import java.io.IOException;
import java.net.URI;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class FollowRedirectTest {
    
    private MockWebServer redirectingServer;
    private MockWebServer redirectedServer;
    
    @Before
    public void setup() throws IOException {
        redirectedServer = new MockWebServer();
        redirectedServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-type", "text/plain")
            .setBody("OK"));       
        redirectingServer = new MockWebServer(); 
        redirectedServer.play();
        redirectingServer.enqueue(new MockResponse()
            .setResponseCode(302)
            .setHeader("Location", "http://localhost:" + redirectedServer.getPort()));
        redirectingServer.play();
    }
    
    @After
    public void shutdown() {
        try {
            redirectedServer.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            redirectingServer.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void testRedirectNotFollowed() throws Exception {
        IClientConfig config = DefaultClientConfigImpl.getClientConfigWithDefaultValues("myclient");
        config.set(CommonClientConfigKey.FollowRedirects, Boolean.FALSE);
        ClientFactory.registerClientFromProperties("myclient", config);
        RestClient client = (RestClient) ClientFactory.getNamedClient("myclient");
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:" + redirectingServer.getPort())).build();
        HttpResponse response = client.executeWithLoadBalancer(request);
        assertEquals(302, response.getStatus());          
    }

    @Test
    public void testRedirectFollowed() throws Exception {
        IClientConfig config = DefaultClientConfigImpl
                .getClientConfigWithDefaultValues("myclient2")
                .set(IClientConfigKey.Keys.FollowRedirects, Boolean.TRUE);
        ClientFactory.registerClientFromProperties("myclient2", config);
        com.netflix.niws.client.http.RestClient client = (com.netflix.niws.client.http.RestClient) ClientFactory.getNamedClient("myclient2");
        HttpRequest request = HttpRequest.newBuilder().uri(new URI("http://localhost:" + redirectingServer.getPort())).build();
        HttpResponse response = client.execute(request);
        assertEquals(200, response.getStatus());      
    }

}

