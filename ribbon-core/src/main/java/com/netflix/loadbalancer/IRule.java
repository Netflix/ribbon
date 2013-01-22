package com.netflix.loadbalancer;

public interface IRule{
    /*
     * choose one alive server from lb.allServers or
     * lb.upServers according to key
     * 
     * @return choosen Server object. NULL is returned if none
     *  server is available 
     */

    Server choose(BaseLoadBalancer lb, Object key);
}
 
