package com.netflix.niws.client;

import java.util.List;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

/**
 * The class that defines how a list of servers are obtained, updated and filtered for use by NIWS
 * @author stonse
 *
 */
public abstract class AbstractNIWSServerList<T extends Server> implements ServerList<T>, IClientConfigAware {   
           
    /**
     * This will be called ONLY ONCE to obtain the Filter class instance
     * Concrete imple
     * @param niwsClientConfig
     * @return
     */
    public AbstractNIWSServerListFilter<T> getFilterImpl(IClientConfig niwsClientConfig) throws NIWSClientException{
        return new AbstractNIWSServerListFilter<T>(){

            @Override
            public List<T> getFilteredListOfServers(List<T> servers) {
                return servers;
            }
        };

    }
}
