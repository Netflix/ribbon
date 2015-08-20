package com.netflix.niws.client.http;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.configuration.Configuration;
import org.junit.*;

import com.netflix.client.ClientFactory;
import com.netflix.client.PrimeConnections.PrimeConnectionEndStats;
//import com.netflix.client.PrimeConnections.PrimeConnectionEndStats;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;
import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.net.httpserver.HttpServer;

public class PrimeConnectionsTest {
    private static String SERVICE_URI;
    private static int port = (new Random()).nextInt(1000) + 4000;
    private static HttpServer server = null;
	
	private static final int SMALL_FIXED_SERVER_LIST_SIZE = 10;
	private static final int LARGE_FIXED_SERVER_LIST_SIZE = 200;

	public static class LargeFixedServerList extends FixedServerList {
	    public LargeFixedServerList() {
	    	super(200);
	    }
	}
	
	public static class SmallFixedServerList extends FixedServerList {
	    public SmallFixedServerList() {
	    	super(10);
	    }
	}
	
	public static class FixedServerList extends AbstractServerList<Server> {
	    private Server testServer = new Server("localhost", port);
	    private List<Server> testServers; 
	    
	    public FixedServerList(int repeatCount) {
			Server list[] = new Server[repeatCount];
			for (int ii = 0; ii < list.length; ii++) {
				list[ii] = testServer;
			}
			testServers = Arrays.asList(list);
	    }
		
		@Override
		public void initWithNiwsConfig(IClientConfig clientConfig) {
		}
		
		@Override
		public List<Server> getInitialListOfServers() {
			return testServers;
		}

		@Override
		public List<Server> getUpdatedListOfServers() {
			return testServers;
		}
	}

	@BeforeClass
	public static void setup(){
        PackagesResourceConfig resourceConfig = new PackagesResourceConfig("com.netflix.niws.client.http");
        SERVICE_URI = "http://localhost:" + port + "/";
        try{
            server = HttpServerFactory.create(SERVICE_URI, resourceConfig);           
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
	
	@Test
	public void testPrimeConnectionsSmallPool() throws Exception {
		Configuration config = ConfigurationManager.getConfigInstance();
		config.setProperty("PrimeConnectionsTest1.ribbon.NFLoadBalancerClassName", 
				com.netflix.loadbalancer.DynamicServerListLoadBalancer.class.getName());
		config.setProperty("PrimeConnectionsTest1.ribbon.NIWSServerListClassName", SmallFixedServerList.class.getName());
		config.setProperty("PrimeConnectionsTest1.ribbon.EnablePrimeConnections", "true");
		DynamicServerListLoadBalancer<Server> lb = (DynamicServerListLoadBalancer<Server>) ClientFactory.getNamedLoadBalancer("PrimeConnectionsTest1");
		PrimeConnectionEndStats stats = lb.getPrimeConnections().getEndStats();
		assertEquals(stats.success, SMALL_FIXED_SERVER_LIST_SIZE);
	}
    
	@Test
	public void testPrimeConnectionsLargePool() throws Exception {
		Configuration config = ConfigurationManager.getConfigInstance();
		config.setProperty("PrimeConnectionsTest2.ribbon.NFLoadBalancerClassName", 
				com.netflix.loadbalancer.DynamicServerListLoadBalancer.class.getName());
		config.setProperty("PrimeConnectionsTest2.ribbon.NIWSServerListClassName", LargeFixedServerList.class.getName());
		config.setProperty("PrimeConnectionsTest2.ribbon.EnablePrimeConnections", "true");
		DynamicServerListLoadBalancer<Server> lb = (DynamicServerListLoadBalancer<Server>) ClientFactory.getNamedLoadBalancer("PrimeConnectionsTest2");
		PrimeConnectionEndStats stats = lb.getPrimeConnections().getEndStats();
		assertEquals(stats.success, LARGE_FIXED_SERVER_LIST_SIZE);
	}
	
    @AfterClass
    public static void shutDown() {
        server.stop(0);
    }
}
