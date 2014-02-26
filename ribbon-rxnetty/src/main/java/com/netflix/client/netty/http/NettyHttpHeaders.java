package com.netflix.client.netty.http;

import java.util.List;
import java.util.Map.Entry;

import io.reactivex.netty.protocol.http.client.HttpResponseHeaders;

class NettyHttpHeaders implements com.netflix.client.http.HttpHeaders {

    final HttpResponseHeaders delegate;
    
    public NettyHttpHeaders(HttpResponseHeaders delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public String getFirstValue(String headerName) {
        return delegate.get(headerName);
    }

    @Override
    public List<String> getAllValues(String headerName) {
        return delegate.getAll(headerName);
    }

    @Override
    public List<Entry<String, String>> getAllHeaders() {
        return delegate.entries();
    }

    @Override
    public boolean containsHeader(String name) {
        return delegate.contains(name);
    }
    
    @Override
    public String toString() {
        return delegate.toString();
    }
}
