package com.netflix.loadbalancer;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.config.ConfigurationManager;

public class ConfigurationBasedServerListTest {

	@Test
	public void testEmptyList() {		
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
