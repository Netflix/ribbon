package com.netflix.loadbalancer;



public interface ClientCallableProvider<T> {

    public T executeOnServer(Server server) throws Exception;
}
