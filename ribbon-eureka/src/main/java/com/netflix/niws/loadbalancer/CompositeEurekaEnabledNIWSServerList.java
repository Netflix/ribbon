package com.netflix.niws.loadbalancer;

import java.util.List;

import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ServerList;

/**
 * @author Tomasz Bak
 */
public class CompositeEurekaEnabledNIWSServerList implements ServerList<DiscoveryEnabledServer>, IClientConfigAware {

    public static final String EUREKA2_INTEREST_ENABLED = "eureka2.interest.enabled";
    public static final CommonClientConfigKey<String> EUREKA2_INTEREST_ENABLED_KEY = new CommonClientConfigKey<String>(EUREKA2_INTEREST_ENABLED) {
    };

    private DiscoveryEnabledNIWSServerList eureka1ServerList;
    private Eureka2EnabledNIWSServerList eureka2ServerList;
    private IClientConfig clientConfig;

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;

        this.eureka1ServerList = new DiscoveryEnabledNIWSServerList();
        this.eureka1ServerList.initWithNiwsConfig(clientConfig);

        this.eureka2ServerList = new Eureka2EnabledNIWSServerList();
        this.eureka2ServerList.initWithNiwsConfig(clientConfig);
    }

    @Override
    public List<DiscoveryEnabledServer> getInitialListOfServers() {
        return loadListOfServers();
    }

    @Override
    public List<DiscoveryEnabledServer> getUpdatedListOfServers() {
        return loadListOfServers();
    }

    private List<DiscoveryEnabledServer> loadListOfServers() {
        if (clientConfig.getPropertyAsBoolean(EUREKA2_INTEREST_ENABLED_KEY, false)) {
            return eureka1ServerList.getUpdatedListOfServers();
        }
        return eureka2ServerList.getUpdatedListOfServers();
    }
}
