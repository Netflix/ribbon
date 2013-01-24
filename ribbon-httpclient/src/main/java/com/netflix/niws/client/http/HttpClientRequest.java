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
package com.netflix.niws.client.http;

import java.net.URI;

import javax.ws.rs.core.MultivaluedMap;

import com.netflix.client.ClientRequest;
import com.netflix.client.config.IClientConfig;

public class HttpClientRequest extends ClientRequest {
        
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

    private MultivaluedMap<String, String> headers;
    private MultivaluedMap<String, String> queryParams;
    private Object entity;
    private Verb verb;
    
    private HttpClientRequest() {
        this.verb = Verb.GET;
    }
    
    public static class Builder {
        
        private HttpClientRequest request = new HttpClientRequest(); 
        
        public Builder setUri(URI uri) {
            request.setUri(uri);
            return this;
        }
        
        public Builder setHeaders(MultivaluedMap<String, String> headers) {
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

        public Builder setQueryParams(MultivaluedMap<String, String> queryParams) {
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
        
        public HttpClientRequest build() {
            return request;    
        }
    }
    
    public MultivaluedMap<String, String> getQueryParams() {
        return queryParams;
    }
    
    public Verb getVerb() {
        return verb;
    }
    
    public MultivaluedMap<String, String> getHeaders() {
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
    public HttpClientRequest replaceUri(URI newURI) {
        return (new Builder()).setUri(newURI)
        .setEntity(this.getEntity())
        .setHeaders(this.getHeaders())
        .setOverrideConfig(this.getOverrideConfig())
        .setQueryParams(this.getQueryParams())
        .setRetriable(this.isRetriable())
        .setLoadBalancerKey(this.getLoadBalancerKey())
        .setVerb(this.getVerb()).build();        
    }
}
