/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.testutils;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.loadbalancer.Server;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;

@RunWith(PowerMockRunner.class)
@PrepareForTest( {DiscoveryManager.class, DiscoveryClient.class} )
@PowerMockIgnore({"javax.management.*", "com.sun.jersey.*", "com.sun.*", "org.apache.*", "weblogic.*", "com.netflix.config.*", "com.sun.jndi.dns.*",
    "javax.naming.*", "com.netflix.logging.*", "javax.ws.*"})

@Ignore
public abstract class MockedDiscoveryServerListTest {
    protected abstract List<Server> getMockServerList();
    
    protected abstract String getVipAddress();

    
    static List<InstanceInfo> getDummyInstanceInfo(String appName, List<Server> serverList){
        List<InstanceInfo> list = new ArrayList<InstanceInfo>();
        for (Server server: serverList) {
            InstanceInfo info = InstanceInfo.Builder.newBuilder().setAppName(appName)
                    .setHostName(server.getHost())
                    .setPort(server.getPort())
                    .setDataCenterInfo(new MyDataCenterInfo(DataCenterInfo.Name.MyOwn))
                    .build();
            list.add(info);
        }
        return list;
    }
        
    @Before
    public void setupMock(){
        List<InstanceInfo> instances = getDummyInstanceInfo("dummy", getMockServerList());
        PowerMock.mockStatic(DiscoveryManager.class);
        PowerMock.mockStatic(DiscoveryClient.class);

        DiscoveryClient mockedDiscoveryClient = createMock(DiscoveryClient.class);
        DiscoveryManager mockedDiscoveryManager = createMock(DiscoveryManager.class);

        expect(mockedDiscoveryClient.getEurekaClientConfig()).andReturn(new DefaultEurekaClientConfig()).anyTimes();
        expect(DiscoveryManager.getInstance()).andReturn(mockedDiscoveryManager).anyTimes();
        expect(mockedDiscoveryManager.getDiscoveryClient()).andReturn(mockedDiscoveryClient).anyTimes();

        expect(mockedDiscoveryClient.getInstancesByVipAddress(getVipAddress(), false, null)).andReturn(instances).anyTimes();

        replay(DiscoveryManager.class);
        replay(DiscoveryClient.class);
        replay(mockedDiscoveryManager);
        replay(mockedDiscoveryClient);
    }

}
