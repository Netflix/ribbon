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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SimpleRoundRobinLBTest {
	static BaseLoadBalancer lb;
    static Map<String, Boolean> isAliveMap = new ConcurrentHashMap<String, Boolean>();
	
	@BeforeClass
	public static void setup(){
		LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
		isAliveMap.put("dummyservice0.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice1.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice3.netflix.com:8080", Boolean.TRUE);
		
		IPing ping = new PingFake();
		IRule rule = new RoundRobinRule();
		lb = new BaseLoadBalancer(ping,rule);
		lb.setPingInterval(1);
		lb.setMaxTotalPingTime(2);
		
		// the setting of servers is done by a call to DiscoveryService
		List<Server> servers = new ArrayList<Server>();
		for (String svc: isAliveMap.keySet()){
			servers.add(new Server(svc));	
		}
		lb.addServers(servers);
		
		// make sure the ping cycle has kicked in and all servers are set to alive
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
		}
		System.out.println(lb.getServerList(true));
	}
	
	@AfterClass
	public static void cleanup() {
		LogManager.getRootLogger().setLevel((Level)Level.INFO);
	}
	
	@Test
	public void testAddingServers() {
		BaseLoadBalancer baseLb = new BaseLoadBalancer(new PingFake(), new RoundRobinRule());
		List<Server> servers = new ArrayList<Server>();
		servers.add(new Server("dummyservice0.netflix.com:8080"));
		baseLb.addServers(servers);
		servers.clear();
		// add 1
		servers.add(new Server("dummyservice1.netflix.com:8080"));
		int originalCount = servers.size();
		baseLb.addServers(servers);
		assertEquals(originalCount, servers.size());
	}
	
	/**
	 * Simulate a single user who should just round robin among the available servers
	 */
	@Test
	public void testRoundRobin(){
		Set<String> servers = new HashSet<String>();
		System.out.println("Simple Round Robin Test");
		for (int i=0; i < 9; i++){
			Server svc = lb.chooseServer("user1");
			servers.add(svc.getId());
		}
		assertEquals(isAliveMap.keySet(), servers);
		System.out.println("Round Robin Test With Server SERVER DOWN");
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.FALSE);
		((BaseLoadBalancer)lb).markServerDown("dummyservice2.netflix.com:8080");
		try {
		    Thread.sleep(3000);
		} catch (Exception e) { // NOPMD			
		}
		for (int i=0; i < 12; i++){
			Server svc = lb.chooseServer("user1");
			assertNotNull(svc);
			System.out.println("server: " + svc.getHost());
			assertFalse(svc.getId().equals("dummyservice2.netflix.com:8080"));
		}
	}	
	
	static class PingFake implements IPing {
		public boolean isAlive(Server server) {
			Boolean res = isAliveMap.get(server.getId());
			return ((res != null) && (res.booleanValue()));
		}
	}
	
}
