package com.netflix.client;

import java.io.InputStream;

import com.google.common.reflect.TypeToken;

public interface ResponseWithTypedEntity extends IResponse {
    public <T> T getEntity(Class<T> type) throws Exception;
    
    public <T> T getEntity(TypeToken<T> type) throws Exception;
    
    public boolean hasEntity();
    
    public InputStream getInputStream() throws ClientException;
}
