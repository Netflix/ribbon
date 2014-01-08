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

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.ClientFactory;
import com.netflix.client.http.HttpRequest;


import com.netflix.client.http.HttpResponse;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.verify;

/**
 *
 * This is essentially a 'live' test variant of {@link com.netflix.niws.loadbalancer.DiscoveryEnabledLoadBalancerSupportsPortOverrideTest}
 * with all of the plumbing working together.
 *
 * Created by jzarfoss on 1/7/14.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {DiscoveryManager.class, DiscoveryClient.class} )
@PowerMockIgnore({"javax.management.*", "org.apache.http.conn.ssl.*"})
public class ForceClientPortTest {

    @Before
    public void setupMock(){

        PowerMock.mockStatic(DiscoveryManager.class);
        PowerMock.mockStatic(DiscoveryClient.class);

        DiscoveryClient mockedDiscoveryClient = createMock(DiscoveryClient.class);
        DiscoveryManager mockedDiscoveryManager = createMock(DiscoveryManager.class);

        expect(DiscoveryClient.getZone((InstanceInfo) EasyMock.anyObject())).andReturn("dummyZone").anyTimes();
        expect(DiscoveryManager.getInstance()).andReturn(mockedDiscoveryManager).anyTimes();
        expect(mockedDiscoveryManager.getDiscoveryClient()).andReturn(mockedDiscoveryClient).anyTimes();

        // I'm only testing non-secure ports here, since the ssl libraries are full of static libraries and they were
        // giving me some headaches with all of the mocks

        expect(mockedDiscoveryClient.getInstancesByVipAddress("google", false, "region")).andReturn(getDummyGoogleInstanceInfo("google", 80)).anyTimes();
        expect(mockedDiscoveryClient.getInstancesByVipAddress("badGoogle", false, "region")).andReturn(getDummyGoogleInstanceInfo("badGoogle", 81)).anyTimes();

        replay(DiscoveryManager.class);
        replay(DiscoveryClient.class);
        replay(mockedDiscoveryManager);
        replay(mockedDiscoveryClient);

    }

    @After
    public void afterMock(){
        verify(DiscoveryManager.class);
        verify(DiscoveryClient.class);
    }




    @Test(expected=com.netflix.client.ClientException.class)
    public void negativeTest() throws Exception{

        // establish that actually using port 81 doesn't work
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.negativeTest.ribbon.DeploymentContextBasedVipAddresses", "badGoogle");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.negativeTest.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.negativeTest.ribbon.NFLoadBalancerClassName", DynamicServerListLoadBalancer.class.getName());
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.negativeTest.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.negativeTest");

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        client.executeWithLoadBalancer(request);

    }


    @Test
    public void testDefaultHonorsServerPortDefinition() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.DeploymentContextBasedVipAddresses", "google");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.Port", "81");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testDefaultHonorsServerPortDefinition.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());

        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.testDefaultHonorsServerPortDefinition");

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);

        assertEquals(200, response.getStatus());
    }


    @Test
    public void testOverrideForcesClientPort() throws Exception{

        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.DeploymentContextBasedVipAddresses", "badGoogle");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.TargetRegion", "region");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.IsSecure", "false");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.Port", "80");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.ForceClientPortConfiguration", "true");
        ConfigurationManager.getConfigInstance().setProperty("ForceClientPortTest.testOverrideForcesClientPort.ribbon.NIWSServerListClassName", DiscoveryEnabledNIWSServerList.class.getName());
        RestClient client = (RestClient) ClientFactory.getNamedClient("ForceClientPortTest.testOverrideForcesClientPort");

        HttpRequest request = HttpRequest.newBuilder().uri(new URI("/")).build();
        HttpResponse response = client.executeWithLoadBalancer(request);

        assertEquals(200, response.getStatus());
    }




    protected static List<InstanceInfo> getDummyGoogleInstanceInfo(String appName, int port){

        List<InstanceInfo> list = new ArrayList<InstanceInfo>();

        InstanceInfo info = InstanceInfo.Builder.newBuilder().setAppName(appName)
                .setHostName("www.google.com")
                .setPort(port)
                .setDataCenterInfo(AmazonInfo.Builder.newBuilder().build())
                .build();

        list.add(info);

        return list;

    }

}
