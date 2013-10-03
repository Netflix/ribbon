package com.netflix.client;

import com.google.common.reflect.TypeToken;

public interface ResponseWithTypedEntity extends IResponse {
    public <T> T get(Class<T> type) throws ClientException;
    
    public <T> T get(TypeToken<T> type) throws ClientException;
    
    public String getAsString() throws ClientException;
}
