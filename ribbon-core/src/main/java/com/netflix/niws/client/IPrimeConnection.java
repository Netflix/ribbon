package com.netflix.niws.client;

import com.netflix.loadbalancer.Server;

public interface IPrimeConnection extends IClientConfigAware {

    public boolean connect(Server server, String uriPath) throws Exception;

}
