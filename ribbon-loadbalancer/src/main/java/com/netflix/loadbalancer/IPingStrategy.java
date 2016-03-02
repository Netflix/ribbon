package com.netflix.loadbalancer;

/**
 * Defines the strategy, used to ping all servers, registered in
 * <c>com.netflix.loadbalancer.BaseLoadBalancer</c>. You would
 * typically create custom implementation of this interface, if you
 * want your servers to be pinged in parallel. <b>Please note,
 * that implementations of this interface should be immutable.</b>
 *
 * @author Dmitry_Cherkas
 * @see Server
 * @see IPing
 */
public interface IPingStrategy {

    boolean[] pingServers(IPing ping, Server[] servers);
}
