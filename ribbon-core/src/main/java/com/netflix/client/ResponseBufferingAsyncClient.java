package com.netflix.client;

import java.io.Closeable;
import java.util.concurrent.Future;

import com.netflix.serialization.SerializationFactory;

public interface ResponseBufferingAsyncClient<T extends ClientRequest, S extends IResponse, U> extends Closeable {
    public Future<S> execute(T request, BufferedResponseCallback<S> callback) throws ClientException;
    
    public void addSerializationFactory(SerializationFactory<U> factory);
}
