package com.netflix.client;

import java.util.Collection;
import java.util.Map;

public interface HttpResponse extends ResponseWithTypedEntity {
    public int getStatus();
    
    public void releaseResources();
    
    public Map<String, Collection<String>> getHeaders();  
}
