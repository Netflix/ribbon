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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.netflix.client.ClientException;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpHeaders;
import com.netflix.client.http.HttpResponse;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

import javax.ws.rs.core.MultivaluedMap;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A NIWS   Client Response
 * (this version just wraps Jersey Client response)
 * @author stonse
 *
 */
class HttpClientResponse implements HttpResponse {
    
    private final ClientResponse bcr;
            
    private final Multimap<String, String> headers = ArrayListMultimap.<String, String>create();
    private final HttpHeaders httpHeaders;
    private final URI requestedURI;
    private final IClientConfig overrideConfig;

    public HttpClientResponse(ClientResponse cr, URI requestedURI, IClientConfig config){
        bcr = cr;
        this.requestedURI = requestedURI;
        this.overrideConfig = config;
        for (Map.Entry<String, List<String>> entry: bcr.getHeaders().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                headers.putAll(entry.getKey(), entry.getValue());
            }
        }
        httpHeaders =  new HttpHeaders() {
            @Override
            public String getFirstValue(String headerName) {
                return bcr.getHeaders().getFirst(headerName);
            }
            @Override
            public List<String> getAllValues(String headerName) {
                return bcr.getHeaders().get(headerName);
            }
            @Override
            public List<Entry<String, String>> getAllHeaders() {
                MultivaluedMap<String, String> map = bcr.getHeaders();
                List<Entry<String, String>> result = Lists.newArrayList();
                for (Map.Entry<String, List<String>> header: map.entrySet()) {
                    String name = header.getKey();
                    for (String value: header.getValue()) {
                        result.add(new AbstractMap.SimpleEntry<String, String>(name, value));
                    }
                }
                return result;
            }

            @Override
            public boolean containsHeader(String name) {
                return bcr.getHeaders().containsKey(name);
            }
        };

    }

     /**
        * Returns the raw entity if available from the response 
        * @return
        * @throws IllegalArgumentException
        */
    public InputStream getRawEntity() {
        return bcr.getEntityInputStream();
    }
       
    
    public <T> T getEntity(Class<T> c) throws Exception {
        return bcr.getEntity(c);
    }

    @Override
    public Map<String, Collection<String>> getHeaders() {
        return headers.asMap();
    }

    @Override
    public int getStatus() {
        return bcr.getStatus();
    }

    @Override
    public boolean isSuccess() {
        boolean isSuccess = false;
        ClientResponse.Status s = bcr != null? bcr.getClientResponseStatus(): null;
        isSuccess = s!=null? (s.getFamily() == javax.ws.rs.core.Response.Status.Family.SUCCESSFUL): false;
        return isSuccess;
    }

    @Override
    public boolean hasEntity() {
        return bcr.hasEntity();
    }
        
    @Override
    public URI getRequestedURI() {
       return requestedURI;
    }
    
    @Override
    public Object getPayload() throws ClientException {
        if (hasEntity()) {
            return getRawEntity();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasPayload() {
        return hasEntity();        
    }
    
    public ClientResponse getJerseyClientResponse() {
        return bcr;
    }
    
    @Override
    public void close() {
        bcr.close();
    }

    @Override
    public InputStream getInputStream() {
        return getRawEntity();
    }

    @Override
    public String getStatusLine() {
        return bcr.getClientResponseStatus().toString();
    }

    @Override
    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    @Override
    public <T> T getEntity(TypeToken<T> type) throws Exception {
        return bcr.getEntity(new GenericType<T>(type.getType()));
    }

    @Override
    public <T> T getEntity(Type type) throws Exception {
        return bcr.getEntity(new GenericType<T>(type));
    }
}
