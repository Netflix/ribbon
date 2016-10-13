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
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.ConfigurationBasedServerList;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.http.RestClient;

public class ClientFactoryTest {
	
    private static IClientConfig config;
	private static RestClient client;
	
	@BeforeClass
	public static void init() {
		ConfigurationManager.getConfigInstance().setProperty("junit.ribbon.listOfServers", "www.example1.come:80,www.example2.come:80,www.example3.come:80");
		config = ClientFactory.getNamedConfig("junit", DefaultClientConfigImpl.class);
		client = (RestClient) ClientFactory.getNamedClient("junit");
	}
	
	@Test
	public void testChooseServers() {
		assertNotNull(client);
		DynamicServerListLoadBalancer lb = (DynamicServerListLoadBalancer) client.getLoadBalancer();
		assertTrue(lb.getServerListImpl() instanceof ConfigurationBasedServerList);
		Set<Server> expected = new HashSet<Server>();
		expected.add(new Server("www.example1.come:80"));
		expected.add(new Server("www.example2.come:80"));
		expected.add(new Server("www.example3.come:80"));
		Set<Server> result = new HashSet<Server>();
		for (int i = 0; i <= 10; i++) {
			Server s = lb.chooseServer();
			result.add(s);			
		}
		assertEquals(expected, result);
	}
	
    @Test
	public void testConcurrentClientLoading() {
        
        int concurrencyCount = 20;
	    
	    long sequentialStart = System.currentTimeMillis();
	    for (int i = 0; i < concurrencyCount; i++) {
	        final String name = "junit_sequential_"+i;
	        ClientFactory.getNamedClient(name);
	        System.out.println(name + " initialized");
	    }
	    long sequentialDuration = System.currentTimeMillis() - sequentialStart;
	    
	    final ExecutorCompletionService<Boolean> executorCompletionService = new ExecutorCompletionService<Boolean>(Executors.newFixedThreadPool(concurrencyCount));
	    
	    long parallelStart = System.currentTimeMillis();
        for (int i = 0; i < concurrencyCount; i++) {
            executorCompletionService.submit(new ClientTask(i), true);
        }
        
        for (int i = 0; i < concurrencyCount; i++) {
            try {
                executorCompletionService.take().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        long parallelDuration = System.currentTimeMillis() - parallelStart;
        
        assertTrue("parallel loading was not faster", parallelDuration < sequentialDuration);
	}
    
    @Test
    public void testRegisterDuplicateClient() throws ClientException {
        String name = "duplicateClient";
        ClientFactory.registerClientFromProperties(name, config);
        
        try {
            ClientFactory.registerClientFromProperties(name, config);
            fail("ClientException was not thrown");
        } catch (ClientException e) {
            // Do Nothing - Exception was expected
        }
    }
    
    @Test
    public void testRegisterDuplicateLoadBalancerConfig() throws ClientException {
        String name = "duplicateLBConfig";
        ClientFactory.registerNamedLoadBalancerFromclientConfig(name, config);
        
        try {
            ClientFactory.registerNamedLoadBalancerFromclientConfig(name, config);
            fail("ClientException was not thrown");
        } catch (ClientException e) {
            // Do Nothing - Exception was expected
        }
    }
    
    @Test
    public void testRegisterDuplicateLoadBalancerProperties() throws ClientException {
        String name = "duplicateLBProp";
        ConfigurationManager.getConfigInstance().setProperty(name+ ".ribbon.listOfServers", "www.example1.come:80,www.example2.come:80,www.example3.come:80");
        ClientFactory.registerNamedLoadBalancerFromProperties(name, DefaultClientConfigImpl.class);
        
        try {
            ClientFactory.registerNamedLoadBalancerFromProperties(name, DefaultClientConfigImpl.class);
            fail("ClientException was not thrown");
        } catch (ClientException e) {
            // Do Nothing - Exception was expected
        }
    }
    
    private static class ClientTask implements Runnable {
        
        final String name;
        ClientTask(final int instance) {
            name = "junit_parallel_"+instance;
        }

        @Override
        public void run() {
            ClientFactory.getNamedClient(name); 
        }
    }

}
