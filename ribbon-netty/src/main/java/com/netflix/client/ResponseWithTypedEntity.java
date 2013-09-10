package com.netflix.client;

public interface ResponseWithTypedEntity extends IResponse {
    public <T> T get(Class<T> type) throws ClientException;
}
