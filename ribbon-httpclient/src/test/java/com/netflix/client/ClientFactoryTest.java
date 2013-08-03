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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.ConfigurationBasedServerList;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.http.RestClient;

public class ClientFactoryTest {
	
	private static RestClient client;
	
	@BeforeClass
	public static void init() {
		ConfigurationManager.getConfigInstance().setProperty("junit.ribbon.listOfServers", "www.microsoft.com:80,www.netflix.com:80,www.google.com:80");
		client = (RestClient) ClientFactory.getNamedClient("junit");
	}
	
	@Test
	public void testChooseServers() {
		assertNotNull(client);
		DynamicServerListLoadBalancer lb = (DynamicServerListLoadBalancer) client.getLoadBalancer();
		assertTrue(lb.getServerListImpl() instanceof ConfigurationBasedServerList);
		Set<Server> expected = new HashSet<Server>();
		expected.add(new Server("www.microsoft.com:80"));
		expected.add(new Server("www.netflix.com:80"));
		expected.add(new Server("www.google.com:80"));
		Set<Server> result = new HashSet<Server>();
		for (int i = 0; i <= 10; i++) {
			Server s = lb.chooseServer();
			result.add(s);			
		}
		assertEquals(expected, result);
	}

}
