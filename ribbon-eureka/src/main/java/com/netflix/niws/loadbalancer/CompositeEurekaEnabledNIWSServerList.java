package com.netflix.niws.loadbalancer;

import java.util.List;

import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ServerList;

/**
 * @author Tomasz Bak
 */
public class CompositeEurekaEnabledNIWSServerList implements ServerList<DiscoveryEnabledServer>, IClientConfigAware {

    private DiscoveryEnabledNIWSServerList eureka1ServerList;
    private Eureka2EnabledNIWSServerList eureka2ServerList;

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
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
        if (Eureka2Clients.isPreferEureka2()) {
            return eureka2ServerList.getUpdatedListOfServers();
        }
        return eureka1ServerList.getUpdatedListOfServers();
    }
}
