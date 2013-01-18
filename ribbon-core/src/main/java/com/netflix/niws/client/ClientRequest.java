package com.netflix.niws.client;

import java.net.URI;

public class ClientRequest implements Cloneable {

    protected URI uri;
    protected Object loadBalancerKey = null;
    protected Boolean isRetriable = null;
    protected NiwsClientConfig overrideConfig;
        
    public ClientRequest() {
    }
    
    public ClientRequest(URI uri) {
        this.uri = uri;
    }

    public ClientRequest(URI uri, Object loadBalancerKey, boolean isRetriable, NiwsClientConfig overrideConfig) {
        this.uri = uri;
        this.loadBalancerKey = loadBalancerKey;
        this.isRetriable = isRetriable;
        this.overrideConfig = overrideConfig;
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
    

    protected final ClientRequest setUri(URI uri) {
        this.uri = uri;
        return this;
    }

    public final Object getLoadBalancerKey() {
        return loadBalancerKey;
    }

    protected final ClientRequest setLoadBalancerKey(Object loadBalancerKey) {
        this.loadBalancerKey = loadBalancerKey;
        return this;
    }

    public boolean isRetriable() {
        return (Boolean.TRUE.equals(isRetriable));
    }

    protected final ClientRequest setRetriable(boolean isRetriable) {
        this.isRetriable = isRetriable;
        return this;
    }

    public final NiwsClientConfig getOverrideConfig() {
        return overrideConfig;
    }

    protected final ClientRequest setOverrideConfig(NiwsClientConfig overrideConfig) {
        this.overrideConfig = overrideConfig;
        return this;
    }
    
    public ClientRequest replaceUri(URI newURI) {
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
