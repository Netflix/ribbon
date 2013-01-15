package com.netflix.niws.client;

import java.net.URI;

public class ClientRequest implements Cloneable {

    private URI uri;
    private Object loadBalancerKey = null;
    private boolean isRetriable = false;
    private NiwsClientConfig overrideConfig;
        
    public ClientRequest() {
    }
    
    public ClientRequest(URI uri) {
        this.uri = uri;
    }

    public ClientRequest(ClientRequest request) {
        this.uri = request.uri;
        this.loadBalancerKey = request.loadBalancerKey;
        this.overrideConfig = request.overrideConfig;
        this.isRetriable = request.isRetriable;
    }

    public final URI getUri() {
        return uri;
    }
    

    public final ClientRequest setUri(URI uri) {
        this.uri = uri;
        return this;
    }

    public final Object getLoadBalancerKey() {
        return loadBalancerKey;
    }

    public final ClientRequest setLoadBalancerKey(Object loadBalancerKey) {
        this.loadBalancerKey = loadBalancerKey;
        return this;
    }

    public final boolean isRetriable() {
        return isRetriable;
    }

    public final ClientRequest setRetriable(boolean isRetriable) {
        this.isRetriable = isRetriable;
        return this;
    }

    public final NiwsClientConfig getOverrideConfig() {
        return overrideConfig;
    }

    public final ClientRequest setOverrideConfig(NiwsClientConfig overrideConfig) {
        this.overrideConfig = overrideConfig;
        return this;
    }
    
    public ClientRequest createWithNewUri(URI newURI) {
        ClientRequest req;
        try {
            req = (ClientRequest) this.clone();
        } catch (CloneNotSupportedException e) {
            req = new ClientRequest(this);
        }
        req.uri = newURI;
        return req;
    }
}
