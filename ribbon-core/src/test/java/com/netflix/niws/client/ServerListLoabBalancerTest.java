package com.netflix.niws.client;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.Server;

public class ServerListLoabBalancerTest {

	static Server[] servers = {new Server("abc", 80), new Server("xyz", 90), new Server("www.netflix.com", 80)};
	static List<Server> serverList = Arrays.asList(servers);
	
	public static class FixedServerList extends AbstractNIWSServerList<Server> {

		@Override
		public void initWithNiwsConfig(NiwsClientConfig niwsClientConfig) {
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
	
	static NIWSDiscoveryLoadBalancer<Server> lb;
	
	@BeforeClass
	public static void init() {
		Configuration config = ConfigurationManager.getConfigInstance();
		config.setProperty("ServerListLoabBalancerTest.niws.client.NFLoadBalancerClassName", 
				com.netflix.niws.client.NIWSDiscoveryLoadBalancer.class.getName());
		config.setProperty("ServerListLoabBalancerTest.niws.client.NIWSServerListClassName", FixedServerList.class.getName());
		lb = (NIWSDiscoveryLoadBalancer<Server>) ClientFactory.getNamedLoadBalancer("ServerListLoabBalancerTest");
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
