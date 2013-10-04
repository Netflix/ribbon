package com.netflix.client;

import java.util.concurrent.Future;

import com.netflix.serialization.Deserializer;

public interface AsyncClient<T extends ClientRequest, S extends IResponse, U> {
    public <E> Future<S> execute(T request, StreamDecoder<E, U> decooder, ResponseCallback<S, E> callback) throws ClientException;
}
