package com.netflix.client;


import com.netflix.loadbalancer.Server;

public interface ClientCallableProvider<T> {

    public T executeOnServer(Server server) throws Exception;
}
