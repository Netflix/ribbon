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
package com.netflix.ribbon.transport.netty.http;

import com.google.common.collect.Lists;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.ribbon.testutils.MockedDiscoveryServerListTest;
import io.netty.buffer.ByteBuf;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;

import java.util.List;

import static org.junit.Assert.assertEquals;

@PowerMockIgnore("com.google.*")
public class DiscoveryLoadBalancerTest extends MockedDiscoveryServerListTest {

    @Override
    protected List<Server> getMockServerList() {
        return Lists.newArrayList(new Server("www.example1.com", 80), new Server("www.example2.com", 80));
    }

    @Override
    protected String getVipAddress() {
        return "myvip";
    }

    @Test
    public void testLoadBalancer() {
        IClientConfig config = IClientConfig.Builder.newBuilder().withDefaultValues()
                .withDeploymentContextBasedVipAddresses(getVipAddress()).build()
                .set(IClientConfigKey.Keys.NIWSServerListClassName, DiscoveryEnabledNIWSServerList.class.getName());
        LoadBalancingHttpClient<ByteBuf, ByteBuf> client = RibbonTransport.newHttpClient(config);
        LoadBalancerContext lbContext = client.getLoadBalancerContext();
        List<Server> serverList = lbContext.getLoadBalancer().getAllServers();
        assertEquals(getMockServerList(), serverList);
    }
}
