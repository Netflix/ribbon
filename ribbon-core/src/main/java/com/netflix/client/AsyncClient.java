package com.netflix.client;

import java.util.concurrent.Future;

import com.netflix.serialization.Deserializer;

public interface AsyncClient<T extends ClientRequest, S extends ResponseWithTypedEntity> {
    public Future<S> execute(T request, ResponseCallback<S> callback) throws ClientException;
}
