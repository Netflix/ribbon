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


import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.niws.client.http.HttpClientRequest;
import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.niws.client.http.RestClient;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.netflix.config.ConfigurationManager.getConfigInstance;

public class ManyShortLivedRequestsSurvivorTest {

    @Test
    public void survive() throws IOException, ClientException, URISyntaxException, InterruptedException {

        String clientName = "RibbonClientTest-loadBalancingDefaultPolicyRoundRobin";
        String serverListKey = clientName + ".ribbon.listOfServers";
        int nbHitsPerServer = 60;
        MockWebServer server1 = new MockWebServer();
        MockWebServer server2 = new MockWebServer();

        for (int i = 0; i < nbHitsPerServer; i++) {
            server1.enqueue(new MockResponse().setResponseCode(200).setBody("server1 success <" + i + ">!"));
            server2.enqueue(new MockResponse().setResponseCode(200).setBody("server2 success <" + i + ">!"));
        }

        server1.play();
        server2.play();

        getConfigInstance().setProperty(serverListKey, hostAndPort(server1.getUrl("")) + "," + hostAndPort(server2.getUrl("")));

        RestClient client = (RestClient) ClientFactory.getNamedClient(clientName);
        HttpClientRequest request;
        for (int i = 0; i < nbHitsPerServer * 2; i++) {
            request = HttpClientRequest.newBuilder().setUri(new URI("/")).build();
            HttpClientResponse response = client.executeWithLoadBalancer(request);
            response.releaseResources();

        }

    }

    static String hostAndPort(URL url) {
        return "localhost:" + url.getPort();
    }
}
