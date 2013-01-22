package com.netflix.loadbalancer;

import java.util.List;

public interface ServerList<T extends Server> {

    public List<T> getInitialListOfServers();
    
    /**
     * Return updated the list of servers. This is called say every 30 secs
     * (configurable) by the Loadbalancer's Ping cycle
     * 
     * @return
     */
    public List<T> getUpdatedListOfServers();   

}
