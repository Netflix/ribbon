package com.netflix.client.netty.http;


import io.reactivex.netty.protocol.http.client.HttpClient;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;

@SuppressWarnings("rawtypes")
public abstract class CachedNettyHttpClient<I, O> extends AbstractNettyHttpClient<I, O> implements Closeable {

    private ConcurrentHashMap<Server, HttpClient<I, O>> rxClientCache;
    
    public CachedNettyHttpClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }

    public CachedNettyHttpClient(IClientConfig config) {
        super(config);
        rxClientCache = new ConcurrentHashMap<Server, HttpClient<I,O>>();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected HttpClient<I, O> getRxClient(String host, int port) {
        Server server = new Server(host, port);
        HttpClient client =  rxClientCache.get(server);
        if (client != null) {
            return client;
        } else {
            client = createRxClient(server);
            HttpClient old = rxClientCache.putIfAbsent(server, client);
            if (old != null) {
                return old;
            } else {
                return client;
            }
        }
    }

    protected abstract HttpClient<I,O> createRxClient(Server server);
    
    protected ConcurrentMap<Server, HttpClient<I, O>> getCurrentHttpClients() {
        return rxClientCache;
    }
    
    protected HttpClient removeClient(Server server) {
        HttpClient client = rxClientCache.remove(server);
        client.shutdown();
        return client;
    }

    @Override
    public void close() throws IOException {
        for (Server server: rxClientCache.keySet()) {
            removeClient(server);
        }
    }
}
