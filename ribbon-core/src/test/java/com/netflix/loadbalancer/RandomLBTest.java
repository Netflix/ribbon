package com.netflix.loadbalancer;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;


public class RandomLBTest {
    static HashMap<String, Boolean> isAliveMap = new HashMap<String, Boolean>();
    static NFLoadBalancer lb;
	
	@BeforeClass
	public static void setup(){
		isAliveMap.put("dummyservice0.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice1.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice3.netflix.com:8080", Boolean.TRUE);
		
		IPing ping = new PingFake();
		IRule rule = new RandomRule();
		lb = new NFLoadBalancer(ping,rule);
		lb.setPingInterval(20);
		lb.setMaxTotalPingTime(5);
		
		// the setting of servers is done by a call to DiscoveryService
		lb.setServers("dummyservice0.netflix.com:8080, dummyservice1.netflix.com:8080,dummyservice2.netflix.com:8080,dummyservice3.netflix.com:8080");
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		
	}
	
	/**
	 * Simulate a single user who should just round robin among the available servers
	 */
	@Test
	public void testRoundRobin(){
		Set<String> servers = new HashSet<String>(); 
		for (int i=0; i < 100; i++){
			Server svc = lb.chooseServer("user1");
			servers.add(svc.getId());
		}
		assertEquals(isAliveMap.keySet(), servers);
	}
	
	static class PingFake implements IPing {
		public boolean isAlive(Server server) {
			Boolean res = isAliveMap.get(server.getId());
			return ((res != null) && (res.booleanValue()));
		}
	}
}
