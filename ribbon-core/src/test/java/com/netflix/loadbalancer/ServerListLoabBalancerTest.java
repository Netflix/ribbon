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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;

public class ServerListLoabBalancerTest {

	static Server[] servers = {new Server("abc", 80), new Server("xyz", 90), new Server("www.netflix.com", 80)};
	static List<Server> serverList = Arrays.asList(servers);
	
	public static class FixedServerList extends AbstractServerList<Server> {
	    
		@Override
		public void initWithNiwsConfig(IClientConfig clientConfig) {
		}
		
		@Override
		public List<Server> getInitialListOfServers() {
			return serverList;
		}

		@Override
		public List<Server> getUpdatedListOfServers() {
			return serverList;
		}
		
	}
	
	static DynamicServerListLoadBalancer<Server> lb;
	
	@BeforeClass
	public static void init() {
		Configuration config = ConfigurationManager.getConfigInstance();
		config.setProperty("ServerListLoabBalancerTest.ribbon.NFLoadBalancerClassName", 
				com.netflix.loadbalancer.DynamicServerListLoadBalancer.class.getName());
		config.setProperty("ServerListLoabBalancerTest.ribbon.NIWSServerListClassName", FixedServerList.class.getName());
		lb = (DynamicServerListLoadBalancer<Server>) ClientFactory.getNamedLoadBalancer("ServerListLoabBalancerTest");
	}
	
    @Test
    public void testChooseServer() {
    	assertNotNull(lb);
    	Set<Server> result = new HashSet<Server>();
    	for (int i = 0; i < 100; i++) {
    		Server s = lb.chooseServer(null);
    		result.add(s);
    	}
    	Set<Server> expected = new HashSet<Server>();
    	expected.addAll(serverList);
    	assertEquals(expected, result);
    }
}
