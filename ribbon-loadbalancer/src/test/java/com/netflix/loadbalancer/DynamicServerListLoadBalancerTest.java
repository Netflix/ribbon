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
package com.netflix.loadbalancer;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.testutil.MockHttpServer;

public class DynamicServerListLoadBalancerTest {
    @ClassRule
    public static MockHttpServer server = new MockHttpServer();
    
    public static class MyServerList extends AbstractServerList<Server> {

        public final static CountDownLatch latch = new CountDownLatch(5);
        public final static AtomicInteger counter = new AtomicInteger(0);
        
        public static final List<Server> list = Lists.newArrayList(new Server(server.getServerUrl()));
        
        public MyServerList() {
        }
        
        public MyServerList(IClientConfig clientConfig) {
        }
        
        @Override
        public List<Server> getInitialListOfServers() {
            return list;
        }

        @Override
        public List<Server> getUpdatedListOfServers() {
            counter.incrementAndGet();
            latch.countDown();
            return list;
        }

        @Override
        public void initWithNiwsConfig(IClientConfig clientConfig) {
        }
    }

    @Test
    public void testDynamicServerListLoadBalancer() throws Exception {
        DefaultClientConfigImpl config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        config.set(CommonClientConfigKey.NIWSServerListClassName, MyServerList.class.getName());
        config.set(CommonClientConfigKey.NFLoadBalancerClassName, DynamicServerListLoadBalancer.class.getName());
        config.set(CommonClientConfigKey.ServerListRefreshInterval, 50);
        DynamicServerListLoadBalancer<Server> lb = new DynamicServerListLoadBalancer<Server>(config);
        try {
            assertTrue(MyServerList.latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) { // NOPMD
        }
        assertEquals(lb.getAllServers(), MyServerList.list);
        lb.stopServerListRefreshing();
        Thread.sleep(1000);
        int count = MyServerList.counter.get();
        assertTrue(count >= 5);
        Thread.sleep(1000);
        assertEquals(count, MyServerList.counter.get());
        
    }
}

