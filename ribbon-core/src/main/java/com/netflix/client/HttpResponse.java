package com.netflix.client;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;

public interface HttpResponse extends ResponseWithTypedEntity, Closeable {
    public int getStatus();
    
    public Map<String, Collection<String>> getHeaders();  
}
