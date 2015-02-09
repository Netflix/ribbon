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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.ILoadBalancer;

public class ClientFactoryTest {

    private static final String CLIENT_FACTORY_MAP_TEST = "clientFactoryMapTest";

    @BeforeClass
    public static void beforeClass() {
        ConfigurationManager.getConfigInstance().setProperty(
                CLIENT_FACTORY_MAP_TEST + ".ribbon."
                        + CommonClientConfigKey.ClientClassName,
                "com.netflix.client.ClientFactoryTestClient");
    }

    @Test
    public void testGetNamedConfigsAndNamedLoadBalancer() {
        IClient<?, ?> namedClient = ClientFactory
                .getNamedClient(CLIENT_FACTORY_MAP_TEST);

        Map<String, IClient<?, ?>> namedClients = ClientFactory
                .getNamedClients();
        assertTrue(namedClients.containsKey(CLIENT_FACTORY_MAP_TEST));
        assertEquals(namedClients.get(CLIENT_FACTORY_MAP_TEST), namedClient);

        ILoadBalancer namedLoadBalancer = ClientFactory
                .getNamedLoadBalancer(CLIENT_FACTORY_MAP_TEST);

        Map<String, ILoadBalancer> namedLoadBalancers = ClientFactory
                .getNamedLoadBalancers();
        assertTrue(namedLoadBalancers.containsKey(CLIENT_FACTORY_MAP_TEST));
        assertEquals(namedLoadBalancers.get(CLIENT_FACTORY_MAP_TEST),
                namedLoadBalancer);
    }

    @Test
    public void testGetNamedConfigs() {
        IClientConfig namedConfig = ClientFactory
                .getNamedConfig(CLIENT_FACTORY_MAP_TEST);

        Map<String, IClientConfig> namedConfigs = ClientFactory
                .getNamedConfigs();
        assertTrue(namedConfigs.containsKey(CLIENT_FACTORY_MAP_TEST));
        assertEquals(namedConfigs.get(CLIENT_FACTORY_MAP_TEST), namedConfig);
    }

}
