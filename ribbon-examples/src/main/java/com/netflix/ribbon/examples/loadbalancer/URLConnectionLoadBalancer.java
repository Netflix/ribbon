package com.netflix.ribbon.examples.loadbalancer;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import com.google.common.collect.Lists;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.LoadBalancerCommand;
import com.netflix.loadbalancer.LoadBalancerExecutor;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;

/**
 * An example to show using {@link LoadBalancerExecutor} to load balance calls made with {@link URLConnection}
 *  
 * @author Allen Wang
 *
 */
public class URLConnectionLoadBalancer {
   
    private final LoadBalancerExecutor lbExecutor;
    // retry handler that does not retry on same server, but on a different server
    private final RetryHandler retryHandler = new DefaultLoadBalancerRetryHandler(0, 1, true);
    
    public URLConnectionLoadBalancer(List<Server> serverList) {
        lbExecutor = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancerExecutor(serverList);
    }
    
    public String call(final String path) throws Exception {
        return lbExecutor.execute(new LoadBalancerCommand<String>() {
            @Override
            public String run(Server server) throws Exception {
                URL url = new URL("http://" + server.getHost() + ":" + server.getPort() + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                return conn.getResponseMessage();
            }
        }, retryHandler);
    }
    
    public LoadBalancerStats getLoadBalancerStats() {
        return ((BaseLoadBalancer) lbExecutor.getLoadBalancer()).getLoadBalancerStats();
    }

    public static void main(String[] args) throws Exception {
        URLConnectionLoadBalancer urlLoadBalancer = new URLConnectionLoadBalancer(Lists.newArrayList(
                new Server("www.google.com", 80),
                new Server("www.linkedin.com", 80),
                new Server("www.yahoo.com", 80)));
        for (int i = 0; i < 6; i++) {
            System.out.println(urlLoadBalancer.call("/"));
        }
        System.out.println("=== Load balancer stats ===");
        System.out.println(urlLoadBalancer.getLoadBalancerStats());
    }
}
