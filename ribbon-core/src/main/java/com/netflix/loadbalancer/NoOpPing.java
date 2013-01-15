package com.netflix.loadbalancer;

/**
 * No Op Ping
 * @author stonse
 *
 */
public class NoOpPing implements IPing {

    @Override
    public boolean isAlive(Server server) {
        return true;
    }

}
