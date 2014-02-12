package com.netflix.client;

import java.util.concurrent.Callable;

import com.netflix.loadbalancer.Server;

public interface ClientCallableProvider<T> {

    public T executeOnServer(Server server) throws Exception;
}
