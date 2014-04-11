package com.netflix.client.http;

import java.util.List;
import java.util.Map.Entry;

public interface HttpHeaders {

    public String getFirstValue(String headerName);
    
    public List<String> getAllValues(String headerName);
    
    public List<Entry<String, String>> getAllHeaders();
    
    public boolean containsHeader(String name);
}
