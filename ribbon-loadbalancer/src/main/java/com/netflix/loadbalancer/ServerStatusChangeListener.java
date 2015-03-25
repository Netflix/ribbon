package com.netflix.loadbalancer;

import java.util.Collection;

public interface ServerStatusChangeListener {

    /**
     * Invoked by {@link BaseLoadBalancer} when server status has changed (e.g. when marked as down or found dead by ping).
     *
     * @param servers the servers that had their status changed, never {@code null}
     */
    public void serverStatusChanged(Collection<Server> servers);

}
