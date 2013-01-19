package com.netflix.loadbalancer;


import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.BeforeClass;
import org.junit.Test;

public class SimpleRoundRobinLBTest {
	static NFLoadBalancer lb;
    static Map<String, Boolean> isAliveMap = new ConcurrentHashMap<String, Boolean>();
	
	@BeforeClass
	public static void setup(){
		isAliveMap.put("dummyservice0.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice1.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice3.netflix.com:8080", Boolean.TRUE);
		
		IPing ping = new PingFake();
		IRule rule = new RoundRobinRule();
		lb = new NFLoadBalancer(ping,rule);
		lb.setPingInterval(5);
		lb.setMaxTotalPingTime(2);
		
		// the setting of servers is done by a call to DiscoveryService
		List<Server> servers = new ArrayList<Server>();
		for (String svc: isAliveMap.keySet()){
			servers.add(new Server(svc));	
		}		
		lb.addServers(servers);
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
		}
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
	}
	
	/**
	 * Simulate a single user who should just round robin among the available servers
	 */
	@Test
	public void testRoundRobinWithAServerFailure() throws Exception {
		System.out.println("Round Robin Test With Server SERVER DOWN");
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.FALSE);
		((NFLoadBalancer)lb).markServerDown("dummyservice2.netflix.com:8080");
		Thread.sleep(3000);
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
