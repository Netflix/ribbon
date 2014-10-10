/*
 *
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.ribbon.transport.netty.http;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Created by awang on 8/4/14.
 */
public class ServerListRefreshTest {

    /**
     * This test ensures that when server list is refreshed in the load balancer, the set of servers
     * which equals to (oldList - newList) should be removed from the map of cached RxClient. Any server
     * that is not part of oldList should stay in the map.
     *
     * @throws IOException
     */
    @Test
    public void testServerListRefresh() throws IOException {
        String content = "Hello world";
        MockWebServer server1 = new MockWebServer();
        MockWebServer server2 = new MockWebServer();
        MockWebServer server3 = new MockWebServer();
        MockResponse mockResponse = new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content);
        server1.enqueue(mockResponse);
        server2.enqueue(mockResponse);
        server3.enqueue(mockResponse);
        server1.play();
        server2.play();
        server3.play();
        try {
            BaseLoadBalancer lb = new BaseLoadBalancer();
            List<Server> initialList = Lists.newArrayList(new Server("localhost", server1.getPort()), new Server("localhost", server2.getPort()));
            lb.setServersList(initialList);
            LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(lb);
            HttpClientRequest<ByteBuf> request = HttpClientRequest.createGet("/");
            client.submit(request).toBlocking().last();
            client.submit(request).toBlocking().last();
            HttpClientRequest<ByteBuf> request2 = HttpClientRequest.createGet("http://localhost:" + server3.getPort());
            client.submit(request2).toBlocking().last();
            Set<Server> cachedServers = client.getRxClients().keySet();
            assertEquals(Sets.newHashSet(new Server("localhost", server1.getPort()), new Server("localhost", server2.getPort()), new Server("localhost", server3.getPort())), cachedServers);
            List<Server> newList = Lists.newArrayList(new Server("localhost", server1.getPort()), new Server("localhost", 99999));
            lb.setServersList(newList);
            cachedServers = client.getRxClients().keySet();
            assertEquals(Sets.newHashSet(new Server("localhost", server1.getPort()), new Server("localhost", server3.getPort())), cachedServers);
        } finally {
            server1.shutdown();
            server2.shutdown();
            server3.shutdown();
        }
    }
}
