package com.netflix.httpasyncclient;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.netflix.client.ClientException;
import com.netflix.client.IResponse;

public class BaseResponse implements IResponse {

    protected HttpResponse response;
    
    public BaseResponse(HttpResponse response) {
        this.response = response;    
    }
    
    @Override
    public Object getPayload() throws ClientException {
        return response.getEntity();
    }

    @Override
    public boolean hasPayload() {
        return response.getEntity() != null;        
    }

    @Override
    public boolean isSuccess() {
        return response.getStatusLine().getStatusCode() == 200;
    }

    @Override
    public URI getRequestedURI() {
        return null;
    }

    @Override
    public Map<String, Collection<String>> getHeaders() {
        Multimap<String, String> map = ArrayListMultimap.create();
        for (Header header: response.getAllHeaders()) {
            map.put(header.getName(), header.getValue());
        }
        return map.asMap();
        
    }
    
    public int getStatus() {
        return response.getStatusLine().getStatusCode();
    }
    
    public boolean hasEntity() {
        return hasPayload();
    }

}
