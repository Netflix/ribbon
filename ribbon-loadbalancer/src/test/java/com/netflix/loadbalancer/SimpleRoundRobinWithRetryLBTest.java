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

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;


public class SimpleRoundRobinWithRetryLBTest {

	static ServerComparator serverComparator = new ServerComparator();
	static HashMap<String, Boolean> isAliveMap = new HashMap<String, Boolean>();
	
	static BaseLoadBalancer lb;
	
	@BeforeClass
	public static void setup(){
		isAliveMap.put("dummyservice0.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice1.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.TRUE);
		isAliveMap.put("dummyservice3.netflix.com:8080", Boolean.TRUE);
		
		IPing ping = new PingFake();
		IRule rule = new RetryRule(new RoundRobinRule(), 200);
		lb = new BaseLoadBalancer(ping,rule);
		lb.setPingInterval(1);
		lb.setMaxTotalPingTime(5);
		
		// the setting of servers is done by a call to DiscoveryService
		lb.setServers("dummyservice0.netflix.com:8080, dummyservice1.netflix.com:8080,dummyservice2.netflix.com:8080,dummyservice3.netflix.com:8080");
		// just once more to see if forceQuickPing will be called
		lb.setServers("dummyservice0.netflix.com:8080, dummyservice1.netflix.com:8080,dummyservice2.netflix.com:8080,dummyservice3.netflix.com:8080, garbage:8080");
		// set it back to original
	    lb.setServers("dummyservice0.netflix.com:8080, dummyservice1.netflix.com:8080,dummyservice2.netflix.com:8080,dummyservice3.netflix.com:8080");
        lb.setServers("dummyservice0.netflix.com:8080, dummyservice1.netflix.com:8080,dummyservice2.netflix.com:8080,dummyservice3.netflix.com:8080");
	      
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
		}
		
	}
		
	/**
	 * Simulate a single user who should just round robin among the available servers
	 */
	@Test
	public void testRoundRobinWithAServerFailure(){
		isAliveMap.put("dummyservice2.netflix.com:8080", Boolean.FALSE);
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {			
		}
		for (int i=0; i < 20; i++){
			Server svc = lb.chooseServer("user1");
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

