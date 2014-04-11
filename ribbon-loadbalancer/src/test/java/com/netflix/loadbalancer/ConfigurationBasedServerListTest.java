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
package com.netflix.loadbalancer;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;

public class ConfigurationBasedServerListTest {

	@Test
	public void testList() {		
		ConfigurationBasedServerList list = new ConfigurationBasedServerList();
		DefaultClientConfigImpl config = new DefaultClientConfigImpl();
		config.setClientName("junit1");
		list.initWithNiwsConfig(config);
		assertTrue(list.getInitialListOfServers().isEmpty());
		ConfigurationManager.getConfigInstance().setProperty("junit1.ribbon.listOfServers", "abc.com:80,microsoft.com,1.2.3.4:8080");
		List<Server> servers = list.getUpdatedListOfServers();
		List<Server> expected = new ArrayList<Server>();
		expected.add(new Server("abc.com:80"));
		expected.add(new Server("microsoft.com:80"));
		expected.add(new Server("1.2.3.4:8080"));
		assertEquals(expected, servers);
		ConfigurationManager.getConfigInstance().setProperty("junit1.ribbon.listOfServers", "");
		assertTrue(list.getUpdatedListOfServers().isEmpty());
		ConfigurationManager.getConfigInstance().clearProperty("junit1.ribbon.listOfServers");
		assertTrue(list.getUpdatedListOfServers().isEmpty());
	}
}
