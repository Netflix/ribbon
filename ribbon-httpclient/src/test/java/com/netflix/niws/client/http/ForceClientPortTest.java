/*
*
* Copyright 2014 Netflix, Inc.
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
package com.netflix.niws.client.http;

import com.netflix.client.ClientFactory;
import com.netflix.client.http.HttpRequest;


import com.netflix.client.http.HttpResponse;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.Server;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 *
 * Verify behavior of the override flag ForceClientPortConfiguration("ForceClientPortConfiguration")
 *
 * WARNING: This feature should be treated as temporary workaround to support
 * more than 2 ports per server until relevant load balancer paradigms
 * (i.e., Eureka Server/Client) can accommodate more than 2 ports in its metadata.
 *
 * See also:   https://github.com/Netflix/eureka/issues/71
 *
 * Created by jzarfoss on 1/6/14.
 */
public class ForceClientPortTest {

    @Test(expected=com.netflix.client.ClientException.class)
    public void negativeTest() throws Exception{

        // establish that actually using port 81 doesn't work

        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.negativeTest");

        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 81)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        client.executeWithLoadBalancer(request);

    }


    @Test
    public void testDefaultHonorsServerPortDefinition() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.Port", "81");
        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.testDefaultHonorsServerPortDefinition");

        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 80)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);

        // this would not work/connect if it were actually using port 81
        assertEquals(200, response.getStatus());

    }

    @Test
    public void testDefaultHonorsServerSecurePortDefinition() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerSecurePortDefinition.ribbon.IsSecure", "true");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerSecurePortDefinition.ribbon.SecurePort", "444");
        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.testDefaultHonorsServerSecurePortDefinition");

        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 443)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);

        // this would not work/connect if it were actually using port 444
        assertEquals(200, response.getStatus());

    }

    @Test
    public void testOverrideForcesClientPort() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.Port", "80");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.ForceClientPortConfiguration", "true");
        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.testOverrideForcesClientPort");

        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 81)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);

        // this would not work/connect if it were actually using port 81
        assertEquals(200, response.getStatus());



    }


    @Test
    public void testOverrideForcesClientSecurePort() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientSecurePort.ribbon.IsSecure", "true");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientSecurePort.ribbon.SecurePort", "443");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientSecurePort.ribbon.ForceClientPortConfiguration", "true");
        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.testOverrideForcesClientSecurePort");

        BaseLoadBalancer lb = new BaseLoadBalancer();
        Server[] servers = new Server[]{new Server("www.google.com", 444)};
        lb.addServers(Arrays.asList(servers));
        client.setLoadBalancer(lb);

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);

        // this would not work/connect if it were actually using port 444
        assertEquals(200, response.getStatus());
    }


}
