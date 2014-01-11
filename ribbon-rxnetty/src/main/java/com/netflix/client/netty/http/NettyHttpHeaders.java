package com.netflix.client.netty.http;

import io.netty.handler.codec.http.HttpResponse;

import java.util.List;
import java.util.Map.Entry;

class NettyHttpHeaders implements com.netflix.client.http.HttpHeaders {

    final HttpResponse delegate;
    
    public NettyHttpHeaders(HttpResponse delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public String getFirstValue(String headerName) {
        return delegate.headers().get(headerName);
    }

    @Override
    public List<String> getAllValues(String headerName) {
        return delegate.headers().getAll(headerName);
    }

    @Override
    public List<Entry<String, String>> getAllHeaders() {
        return delegate.headers().entries();
    }

    @Override
    public boolean containsHeader(String name) {
        return delegate.headers().contains(name);
    }
}
