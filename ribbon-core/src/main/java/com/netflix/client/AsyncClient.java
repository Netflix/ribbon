package com.netflix.client;

import java.util.concurrent.Future;

public interface AsyncClient<T extends ClientRequest, S extends IResponse, U, V> extends ResponseBufferingAsyncClient<T, S, V> {
    public <E> Future<S> execute(T request, StreamDecoder<E, U> decooder, ResponseCallback<S, E> callback) throws ClientException;
}
