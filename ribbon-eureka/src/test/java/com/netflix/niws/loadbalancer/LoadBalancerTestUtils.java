package com.netflix.niws.loadbalancer;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;

import java.util.ArrayList;
import java.util.List;

import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.createMock;

public class LoadBalancerTestUtils
{
    static List<InstanceInfo> getDummyInstanceInfo(String appName, String host, String ipAddr, int port){
        List<InstanceInfo> list = new ArrayList<InstanceInfo>();

        InstanceInfo info = InstanceInfo.Builder.newBuilder().setAppName(appName)
                .setHostName(host)
                .setIPAddr(ipAddr)
                .setPort(port)
                .setDataCenterInfo(new MyDataCenterInfo(DataCenterInfo.Name.MyOwn))
                .build();

        list.add(info);

        return list;
    }

    static DiscoveryClient mockDiscoveryClient()
    {
        DiscoveryClient mockedDiscoveryClient = createMock(DiscoveryClient.class);
        expect(mockedDiscoveryClient.getEurekaClientConfig()).andReturn(new DefaultEurekaClientConfig()).anyTimes();
        return mockedDiscoveryClient;
    }
}
