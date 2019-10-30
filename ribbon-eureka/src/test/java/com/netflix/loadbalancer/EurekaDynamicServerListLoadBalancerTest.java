package com.netflix.loadbalancer;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.niws.loadbalancer.EurekaNotificationServerListUpdater;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.inject.Provider;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A test for {@link com.netflix.loadbalancer.DynamicServerListLoadBalancer} using the
 * {@link com.netflix.niws.loadbalancer.EurekaNotificationServerListUpdater} instead of the default.
 *
 * @author David Liu
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(DiscoveryClient.class)
@PowerMockIgnore("javax.management.*")
public class EurekaDynamicServerListLoadBalancerTest {

    private final List<InstanceInfo> servers = InstanceInfoGenerator.newBuilder(10, 1).build().toInstanceList();
    private final int initialServerListSize = 4;
    private final int secondServerListSize = servers.size() - initialServerListSize;
    private final String vipAddress = servers.get(0).getVIPAddress();


    private DefaultClientConfigImpl config;
    private EurekaClient eurekaClientMock;
    private Provider<EurekaClient> eurekaClientProvider;

    @Before
    public void setUp() {
        PowerMock.mockStatic(DiscoveryClient.class);

        EasyMock
                .expect(DiscoveryClient.getZone(EasyMock.isA(InstanceInfo.class)))
                .andReturn("zone")
                .anyTimes();

        eurekaClientMock = setUpEurekaClientMock(servers);
        eurekaClientProvider = new Provider<EurekaClient>() {
            @Override
            public EurekaClient get() {
                return eurekaClientMock;
            }
        };

        config = DefaultClientConfigImpl.getClientConfigWithDefaultValues();
        config.set(CommonClientConfigKey.DeploymentContextBasedVipAddresses, vipAddress);
        config.set(CommonClientConfigKey.ServerListUpdaterClassName, EurekaNotificationServerListUpdater.class.getName());
    }


    @Test
    public void testLoadBalancerHappyCase() throws Exception {
        Assert.assertNotEquals("the two test server list counts should be different",
                secondServerListSize, initialServerListSize);

        DynamicServerListLoadBalancer<DiscoveryEnabledServer> lb = null;
        try {
            Capture<EurekaEventListener> eventListenerCapture = new Capture<EurekaEventListener>();
            eurekaClientMock.registerEventListener(EasyMock.capture(eventListenerCapture));

            PowerMock.replay(DiscoveryClient.class);
            PowerMock.replay(eurekaClientMock);

            // actual testing
            // initial creation and loading of the first serverlist
            lb = new DynamicServerListLoadBalancer<DiscoveryEnabledServer>(
                    config,
                    new AvailabilityFilteringRule(),
                    new DummyPing(),
                    new DiscoveryEnabledNIWSServerList(vipAddress, eurekaClientProvider),
                    new ZoneAffinityServerListFilter<DiscoveryEnabledServer>(),
                    new EurekaNotificationServerListUpdater(eurekaClientProvider)
            );

            Assert.assertEquals(initialServerListSize, lb.getServerCount(false));

            // trigger an eureka CacheRefreshEvent
            eventListenerCapture.getValue().onEvent(new CacheRefreshedEvent());

            Assert.assertTrue(verifyFinalServerListCount(secondServerListSize, lb));

        } finally {
            if (lb != null) {
                lb.shutdown();

                PowerMock.verify(eurekaClientMock);
                PowerMock.verify(DiscoveryClient.class);
            }
        }
    }

    @Test
    public void testShutdownMultiple() {
        try {
            eurekaClientMock.registerEventListener(EasyMock.anyObject(EurekaEventListener.class));
            EasyMock.expectLastCall().anyTimes();

            PowerMock.replay(DiscoveryClient.class);
            PowerMock.replay(eurekaClientMock);

            DynamicServerListLoadBalancer<DiscoveryEnabledServer> lb1 = new DynamicServerListLoadBalancer<DiscoveryEnabledServer>(
                    config,
                    new AvailabilityFilteringRule(),
                    new DummyPing(),
                    new DiscoveryEnabledNIWSServerList(vipAddress, eurekaClientProvider),
                    new ZoneAffinityServerListFilter<DiscoveryEnabledServer>(),
                    new EurekaNotificationServerListUpdater(eurekaClientProvider)
            );

            DynamicServerListLoadBalancer<DiscoveryEnabledServer> lb2 = new DynamicServerListLoadBalancer<DiscoveryEnabledServer>(
                    config,
                    new AvailabilityFilteringRule(),
                    new DummyPing(),
                    new DiscoveryEnabledNIWSServerList(vipAddress, eurekaClientProvider),
                    new ZoneAffinityServerListFilter<DiscoveryEnabledServer>(),
                    new EurekaNotificationServerListUpdater(eurekaClientProvider)
            );

            DynamicServerListLoadBalancer<DiscoveryEnabledServer> lb3 = new DynamicServerListLoadBalancer<DiscoveryEnabledServer>(
                    config,
                    new AvailabilityFilteringRule(),
                    new DummyPing(),
                    new DiscoveryEnabledNIWSServerList(vipAddress, eurekaClientProvider),
                    new ZoneAffinityServerListFilter<DiscoveryEnabledServer>(),
                    new EurekaNotificationServerListUpdater(eurekaClientProvider)
            );

            lb3.shutdown();
            lb1.shutdown();
            lb2.shutdown();
        } finally {
            PowerMock.verify(eurekaClientMock);
            PowerMock.verify(DiscoveryClient.class);
        }
    }

    // a hacky thread sleep loop to get around some minor async behaviour
    // max wait time is 2 seconds, but should complete well before that.
    private boolean verifyFinalServerListCount(int finalCount, DynamicServerListLoadBalancer lb) throws Exception {
        long stepSize = TimeUnit.MILLISECONDS.convert(50l, TimeUnit.MILLISECONDS);
        long maxTime = TimeUnit.MILLISECONDS.convert(2l, TimeUnit.SECONDS);

        for (int i = 0; i < maxTime; i += stepSize) {
            if (finalCount == lb.getServerCount(false)) {
                return true;
            } else {
                Thread.sleep(stepSize);
            }
        }

        return false;
    }

    private EurekaClient setUpEurekaClientMock(List<InstanceInfo> servers) {
        final EurekaClient eurekaClientMock = PowerMock.createMock(EurekaClient.class);

        EasyMock.expect(eurekaClientMock.getEurekaClientConfig()).andReturn(new DefaultEurekaClientConfig()).anyTimes();

        EasyMock
                .expect(eurekaClientMock.getInstancesByVipAddress(EasyMock.anyString(), EasyMock.anyBoolean(), EasyMock.anyString()))
                .andReturn(servers.subList(0, initialServerListSize)).times(1)
                .andReturn(servers.subList(initialServerListSize, servers.size())).anyTimes();

        EasyMock
                .expectLastCall();

        EasyMock
                .expect(eurekaClientMock.unregisterEventListener(EasyMock.isA(EurekaEventListener.class)))
                .andReturn(true).anyTimes();

        return eurekaClientMock;
    }
}