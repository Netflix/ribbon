package com.netflix.niws.loadbalancer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.client.SimpleVipAddressResolver;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;

import static com.netflix.client.config.CommonClientConfigKey.DeploymentContextBasedVipAddresses;
import static com.netflix.client.config.CommonClientConfigKey.VipAddressResolverClassName;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class Eureka2EnabledNIWSServerListTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.WebServer.build();

    @Rule
    public EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 1);

    private final DefaultClientConfigImpl clientConfig = new DefaultClientConfigImpl();

    private EurekaRegistrationClient registrationClient;


    @Before
    public void setUp() throws Exception {
        ServerResolver registrationResolver = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster().registrationResolver();
        registrationClient = Eurekas.newRegistrationClientBuilder().withServerResolver(registrationResolver).build();

        // Register an instance, and block until registration is confirmed
        RegistrationObservable registration = registrationClient.register(Observable.just(INSTANCE));
        registration.subscribe();
        registration.initialRegistrationResult().timeout(30, TimeUnit.SECONDS).toBlocking().firstOrDefault(null);

        String vipAddress = eurekaDeploymentResource.getEurekaDeployment().getReadCluster().getVip();
        clientConfig.set(Eureka2EnabledNIWSServerList.EUREKA2_READ_CLUSTER_VIP_KEY, vipAddress);

        // Load balancer client related config
        clientConfig.set(DeploymentContextBasedVipAddresses, INSTANCE.getVipAddress());
        clientConfig.set(VipAddressResolverClassName, SimpleVipAddressResolver.class.getName());
    }

    @After
    public void tearDown() throws Exception {
        if (registrationClient != null) {
            registrationClient.shutdown();
        }
        Eureka2Clients.setUseEureka2(false);
        EurekaInterestClient lastInterestClient = Eureka2Clients.setInterestClient(null);
        if (lastInterestClient != null) {
            lastInterestClient.shutdown();
        }
    }

    @Test
    public void testResolveServerListFromEureka2Cluster() throws Exception {
        Eureka2EnabledNIWSServerList eureka2EnabledNIWSServerList = connectViaConfiguration();

        List<DiscoveryEnabledServer> resultServerList = eureka2EnabledNIWSServerList.getInitialListOfServers();
        assertThat(resultServerList.size(), is(equalTo(1)));
        assertThat(resultServerList.get(0).getInstanceInfo().getAppName(), is(equalToIgnoringCase(INSTANCE.getApp())));
    }

    @Test
    public void testReconnectWithNewSingletonInterestClientIfPreviousWasShutDown() throws Exception {
        Eureka2EnabledNIWSServerList eureka2EnabledNIWSServerList = connectViaSingleton();

        List<DiscoveryEnabledServer> resultServerList = eureka2EnabledNIWSServerList.getInitialListOfServers();
        assertThat(resultServerList.size(), is(equalTo(1)));

        // Shutdown the singleton interest client
        Eureka2Clients.setInterestClient(null).shutdown();

        // Access without configured interest client results in an exception
        try {
            eureka2EnabledNIWSServerList.getUpdatedListOfServers();
            fail("InterestClient has been shutdown; getUpdatedListOfServers should fail with IllegalArgumentException");
        } catch (IllegalArgumentException ignore) {
            // As expected
        }

        // Now enable singleton again
        injectSingletonClient();

        List<DiscoveryEnabledServer> updatedListOfServers = eureka2EnabledNIWSServerList.getUpdatedListOfServers();
        assertThat(updatedListOfServers.size(), is(equalTo(1)));
    }

    private Eureka2EnabledNIWSServerList connectViaSingleton() {
        injectSingletonClient();

        Eureka2EnabledNIWSServerList eureka2EnabledNIWSServerList = new Eureka2EnabledNIWSServerList();
        eureka2EnabledNIWSServerList.initWithNiwsConfig(clientConfig);
        return eureka2EnabledNIWSServerList;
    }

    private void injectSingletonClient() {
        Eureka2Clients.setUseEureka2(true);
        Eureka2Clients.setInterestClient(eurekaDeploymentResource.interestClientToReadCluster());
    }

    private Eureka2EnabledNIWSServerList connectViaConfiguration() {
        // Set embedded cluster configuration
        clientConfig.set(Eureka2EnabledNIWSServerList.EUREKA2_WRITE_CLUSTER_HOST_KEY, "localhost");

        int interestPort = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster().getServer(0).getDiscoveryPort();
        clientConfig.set(Eureka2EnabledNIWSServerList.EUREKA2_WRITE_CLUSTER_INTEREST_PORT_KEY, interestPort);

        Eureka2EnabledNIWSServerList eureka2EnabledNIWSServerList = new Eureka2EnabledNIWSServerList();
        eureka2EnabledNIWSServerList.initWithNiwsConfig(clientConfig);
        return eureka2EnabledNIWSServerList;
    }
}