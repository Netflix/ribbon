package com.netflix.client.http;

import java.util.List;

public interface HttpHeaders {

    public String getFirst(String headerName);
    
    public List<String> getAll(String headerName);
}
