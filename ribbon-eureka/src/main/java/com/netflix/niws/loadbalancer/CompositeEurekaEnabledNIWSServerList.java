package com.netflix.niws.loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;

/**
 * @author Tomasz Bak
 */
public class CompositeEurekaEnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer> {

    private DiscoveryEnabledNIWSServerList eureka1ServerList;
    private final AtomicReference<Eureka2EnabledNIWSServerList> eureka2ServerListRef = new AtomicReference<>();
    private IClientConfig clientConfig;

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        this.eureka1ServerList = new DiscoveryEnabledNIWSServerList();
        this.eureka1ServerList.initWithNiwsConfig(clientConfig);
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
        if (Eureka2Clients.isUseEureka2()) {
            if (eureka2ServerListRef.get() == null) { // lazy creation of Eureka2 server list provider
                synchronized (eureka2ServerListRef) {
                    Eureka2EnabledNIWSServerList eureka2ServerList = new Eureka2EnabledNIWSServerList();
                    eureka2ServerList.initWithNiwsConfig(clientConfig);
                    eureka2ServerListRef.set(eureka2ServerList);
                }
            }
            return eureka2ServerListRef.get().getUpdatedListOfServers();
        }
        // Destroy Eureka2 server list provider, if Eureka2 mode has been turned off
        Eureka2EnabledNIWSServerList eureka2ServerList = eureka2ServerListRef.getAndSet(null);
        if (eureka2ServerList != null) {
            eureka2ServerList.shutdown();
        }
        return eureka1ServerList.getUpdatedListOfServers();
    }
}
