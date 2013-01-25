package com.netflix.client.samples;

import java.net.URI;

import org.junit.Ignore;

import com.netflix.client.ClientFactory;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.niws.client.http.HttpClientRequest;
import com.netflix.niws.client.http.HttpClientResponse;
import com.netflix.niws.client.http.RestClient;

@Ignore
public class SampleApp {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
        ConfigurationManager.loadPropertiesFromResources("sample-client.properties");
        System.out.println(ConfigurationManager.getConfigInstance().getProperty("sample-client.ribbon.listOfServers"));
        RestClient client = (RestClient) ClientFactory.getNamedClient("sample-client");
        HttpClientRequest request = HttpClientRequest.newBuilder().setUri(new URI("/")).build();
        for (int i = 0; i < 20; i++)  {
        	HttpClientResponse response = client.executeWithLoadBalancer(request);
        	System.out.println("Status code for " + response.getRequestedURI() + "  :" + response.getStatus());
        }
        ZoneAwareLoadBalancer lb = (ZoneAwareLoadBalancer) client.getLoadBalancer();
        System.out.println(lb.getLoadBalancerStats());
        ConfigurationManager.getConfigInstance().setProperty("sample-client.ribbon.listOfServers", "www.linkedin.com:80,www.google.com:80");
        System.out.println("changing servers ...");
        Thread.sleep(3000);
        for (int i = 0; i < 20; i++)  {
        	HttpClientResponse response = client.executeWithLoadBalancer(request);
        	System.out.println("Status code for " + response.getRequestedURI() + "  : " + response.getStatus());
        }
        System.out.println(lb.getLoadBalancerStats());
	}

}
