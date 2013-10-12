package com.netflix.client.http;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;

import com.netflix.client.ResponseWithTypedEntity;

public interface HttpResponse extends ResponseWithTypedEntity, Closeable {
    public int getStatus();
    
    public Map<String, Collection<String>> getHeaders();  
    
    public void close();
}
