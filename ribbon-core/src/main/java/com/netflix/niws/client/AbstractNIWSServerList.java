package com.netflix.niws.client;

import java.util.List;

import com.netflix.loadbalancer.Server;

/**
 * The class that defines how a list of servers are obtained, updated and filtered for use by NIWS
 * @author stonse
 *
 */
abstract class AbstractNIWSServerList<T extends Server> implements NiwsClientConfigAware{   
       
    /**
     * Return initial list of servers
     * @return
     */
    public abstract List<T> getInitialListOfServers();
    
    /**
     * Return updated the list of servers. This is called say every 30 secs
     * (configurable) by the Loadbalancer's Ping cycle
     * 
     * @return
     */
    public abstract List<T> getUpdatedListOfServers();   
    
    /**
     * This will be called ONLY ONCE to obtain the Filter class instance
     * Concrete imple
     * @param niwsClientConfig
     * @return
     */
    public AbstractNIWSServerListFilter<T> getFilterImpl(NiwsClientConfig niwsClientConfig) throws NIWSClientException{
        return new AbstractNIWSServerListFilter<T>(){

            @Override
            public List<T> getFilteredListOfServers(List<T> servers) {
                return servers;
            }
        };

    }
    
    
   
}
