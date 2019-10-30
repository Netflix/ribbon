/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.netflix.client.config.IClientConfigKey.Keys;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.testutils.MockedDiscoveryServerListTest;
import io.netty.buffer.ByteBuf;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by awang on 7/15/14.
 */
@PowerMockIgnore("com.google.*")
public class DiscoveryEnabledServerListTest extends MockedDiscoveryServerListTest {

    static MockWebServer server;

    @BeforeClass
    public static void init() throws IOException {
        server = new MockWebServer();
        String content = "Hello world";
        MockResponse response = new MockResponse().setResponseCode(200).setHeader("Content-type", "text/plain")
                .setBody(content);
        server.enqueue(response);
        server.play();
    }

    @AfterClass
    public static void shutdown() {
        try {
            server.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected List<Server> getMockServerList() {
        List<Server> servers = new ArrayList<Server>();
        servers.add(new Server("localhost", 12345));
        servers.add(new Server("localhost", server.getPort()));
        return servers;
    }

    @Override
    protected String getVipAddress() {
        return "MyService";
    }

    @Test
    public void testDynamicServers() {
        ConfigurationManager.getConfigInstance().setProperty("MyService.ribbon." + Keys.DeploymentContextBasedVipAddresses, getVipAddress());
        ConfigurationManager.getConfigInstance().setProperty("MyService.ribbon." + Keys.NIWSServerListClassName, DiscoveryEnabledNIWSServerList.class.getName());
        HttpResourceGroup group = Ribbon.createHttpResourceGroupBuilder("MyService")
                .withClientOptions(ClientOptions.create()
                        .withMaxAutoRetriesNextServer(3)
                        .withReadTimeout(300000)).build();
        HttpRequestTemplate<ByteBuf> template = group.newTemplateBuilder("test", ByteBuf.class)
                .withUriTemplate("/")
                .withMethod("GET").build();
        RibbonRequest<ByteBuf> request = template
                .requestBuilder().build();
        String result = request.execute().toString(Charset.defaultCharset());
        assertEquals("Hello world", result);
    }

}
