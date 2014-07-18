/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.client.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;


import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.client.ClientRequest;
import com.netflix.client.config.IClientConfig;

/**
 * Request for HTTP communication.
 * 
 * @author awang
 *
 */
public class HttpRequest extends ClientRequest {
        
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

    protected CaseInsensitiveMultiMap httpHeaders = new CaseInsensitiveMultiMap();
    protected Multimap<String, String> queryParams = ArrayListMultimap.create();
    private Object entity;
    protected Verb verb;
    
    HttpRequest() {
        this.verb = Verb.GET;
    }
    
    public static class Builder {
        
        private HttpRequest request = new HttpRequest(); 
        
        public Builder() {
        }

        public Builder(HttpRequest request) {
            this.request = request;
        }
        
        public Builder uri(URI uri) {
            request.setUri(uri);
            return this;
        }
        
        public Builder uri(String uri) {
            try {
                request.setUri(new URI(uri));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return this;
        }
        
        public Builder header(String name, String value) {
            request.httpHeaders.addHeader(name, value);
            return this;
        }
        
        Builder queryParams(Multimap<String, String> queryParams) {
            request.queryParams = queryParams;
            return this;
        }

        /**
         * @deprecated request configuration should be now be passed 
         *            as a method parameter to client's execution API 
         */
        @Deprecated
        public Builder overrideConfig(IClientConfig config) {
            request.setOverrideConfig(config);
            return this;
        }

        Builder headers(CaseInsensitiveMultiMap headers) {
            request.httpHeaders = headers;
            return this;
        }
        
        public Builder setRetriable(boolean retriable) {
            request.setRetriable(retriable);
            return this;
        }

        /**
         * @deprecated see {@link #queryParam(String, String)}
         */
        @Deprecated
        public Builder queryParams(String name, String value) {
            request.queryParams.put(name, value);
            return this;
        }
        
        public Builder queryParam(String name, String value) {
            request.queryParams.put(name, value);
            return this;
        }

        public Builder entity(Object entity) {
            request.entity = entity;
            return this;
        }


        public Builder verb(Verb verb) {
            request.verb = verb;
            return this;
        }
        
        public Builder loadBalancerKey(Object loadBalancerKey) {
            request.setLoadBalancerKey(loadBalancerKey);
            return this;
        }
        
        public HttpRequest build() {
            return request;    
        }
    }
    
    public Map<String, Collection<String>> getQueryParams() {
        return queryParams.asMap();
    }
    
    public Verb getVerb() {
        return verb;
    }
    
    /**
     * Replaced by {@link #getHttpHeaders()}
     */
    @Deprecated
    public Map<String, Collection<String>>  getHeaders() {
        return httpHeaders.asMap();
    }
    
    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }
    
    public Object getEntity() {
        return entity;
    }
        
    /**
     * Test if the request is retriable. If the request is
     * a {@link Verb#GET} and {@link Builder#setRetriable(boolean)}
     * is not called, returns true. Otherwise, returns value passed in
     * {@link Builder#setRetriable(boolean)}
     */
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
    
    public static Builder newBuilder(HttpRequest toCopy) {
        return new Builder(toCopy);
    }


    /**
     * Return a new instance of HttpRequest replacing the URI.
     */
    @Override
    public HttpRequest replaceUri(URI newURI) {
        return (new Builder()).uri(newURI)
        .headers(this.httpHeaders)
        .overrideConfig(this.getOverrideConfig())
        .queryParams(this.queryParams)
        .setRetriable(this.isRetriable())
        .loadBalancerKey(this.getLoadBalancerKey())
        .verb(this.getVerb())
        .entity(this.entity)
        .build();        
    }
}
