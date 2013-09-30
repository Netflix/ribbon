package com.netflix.client.netty.http;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;


import com.netflix.client.ClientRequest;
import com.netflix.client.config.IClientConfig;

public class NettyHttpRequest extends ClientRequest {
    
    public enum Verb {
        GET("GET"),
        PUT("PUT"),
        POST("POST"),
        DELETE("DELETE"),
        OPTIONS("OPTIONS"),
        HEAD("HEAD");

        private final String verb; // http method

        Verb(String verb) {
            this.verb = verb;
        }

        public String verb() {
            return verb;
        }
    }

    private Map<String, Collection<String>> headers;
    private Map<String, Collection<String>> queryParams;
    private Object entity;
    private Verb verb;
    
    private NettyHttpRequest() {
        this.verb = Verb.GET;
    }
    
    public static class Builder {
        
        private NettyHttpRequest request = new NettyHttpRequest(); 
        
        public Builder setUri(URI uri) {
            request.setUri(uri);
            return this;
        }
        
        public Builder setHeaders(Map<String, Collection<String>> headers) {
            request.headers = headers;
            return this;
        }
        
        public Builder setOverrideConfig(IClientConfig config) {
            request.setOverrideConfig(config);
            return this;
        }

        public Builder setRetriable(boolean retriable) {
            request.setRetriable(retriable);
            return this;
        }

        public Builder setQueryParams(Map<String, Collection<String>> queryParams) {
            request.queryParams = queryParams;
            return this;
        }

        public Builder setEntity(Object entity) {
            request.entity = entity;
            return this;
        }

        public Builder setVerb(Verb verb) {
            request.verb = verb;
            return this;
        }
        
        public Builder setLoadBalancerKey(Object loadBalancerKey) {
            request.setLoadBalancerKey(loadBalancerKey);
            return this;
        }
        
        public NettyHttpRequest build() {
            return request;    
        }
    }
    
    public Map<String, Collection<String>> getQueryParams() {
        return queryParams;
    }
    
    public Verb getVerb() {
        return verb;
    }
    
    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }
    
    public Object getEntity() {
        return entity;
    }
        
    @Override
    public boolean isRetriable() {
        if (this.verb == Verb.GET && isRetriable == null) {
            return true;
        }
        return super.isRetriable();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public NettyHttpRequest replaceUri(URI newURI) {
        return (new Builder()).setUri(newURI)
        .setEntity(this.getEntity())
        .setHeaders(this.getHeaders())
        .setOverrideConfig(this.getOverrideConfig())
        .setQueryParams(this.getQueryParams())
        .setRetriable(this.isRetriable())
        .setLoadBalancerKey(this.getLoadBalancerKey())
        .setVerb(this.getVerb()).build();        
    }
/*    
    public HttpRequest convertToNetty() {
        HttpRequest request = null;
        if (this.getEntity() != null) {
            request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(this.verb.toString()), this.uri.toString());   
        } else {
            
             request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(this.verb.toString()), this.uri.toString(), content)
        }
    }
    */
}
