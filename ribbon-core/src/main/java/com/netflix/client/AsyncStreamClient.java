package com.netflix.client;

import java.util.concurrent.Future;

public interface AsyncStreamClient<T extends ClientRequest, S extends IResponse, U> {
    public <E> Future<S> stream(T request, StreamDecoder<E, U> decooder, StreamResponseCallback<S, E> callback) throws Exception;
}
