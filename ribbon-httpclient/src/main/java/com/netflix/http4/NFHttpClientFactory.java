package com.netflix.http4;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.http.client.HttpClient;

import com.netflix.servo.monitor.Monitors;

/**
 * Factory class to get an instance of NFHttpClient
 * @author stonse
 *
 */
public class NFHttpClientFactory {

	private static Map<MultiKey,NFHttpClient> clientMap = new ConcurrentHashMap<MultiKey,NFHttpClient>();
	
	private static Map<String,NFHttpClient> namedClientMap = new ConcurrentHashMap<String,NFHttpClient>();
	
	private static NFHttpClient defaultClient = new NFHttpClient();	
	
	private static Object lock = new Object();
	
	public static NFHttpClient getNFHttpClient(String host, int port){
		MultiKey mk = new MultiKey(host,port);
		NFHttpClient client = clientMap.get(mk);
		if (client == null){
			client = new NFHttpClient(host, port);
			clientMap.put(mk,client);
		}
		return client;			
	}
	
	public static NFHttpClient getNamedNFHttpClient(String name){		
		NFHttpClient client = namedClientMap.get(name);		
		//avoid creating multiple HttpClient instances 
		if (client == null){
		    synchronized (lock) {
		        client = namedClientMap.get(name);       
		        if (client == null){
        			client = new NFHttpClient(name);
        			namedClientMap.put(name,client);
        			Monitors.registerObject("HttpClient_" + name, client);
		        }
		    }
		}
		return client;	
	}
	

	public static NFHttpClient getDefaultClient() {
		return defaultClient;
	}

	public static void setDefaultClient(NFHttpClient defaultClient) {
		NFHttpClientFactory.defaultClient = defaultClient;
	}	
	
	public static void shutdownNFHttpClient(String name) {
	    HttpClient c = namedClientMap.get(name);
	    if(c != null) {
	        c.getConnectionManager().shutdown();
	        namedClientMap.remove(name);
	        Monitors.unregisterObject("HttpClient_" + name, c);
	    }
	}
}
