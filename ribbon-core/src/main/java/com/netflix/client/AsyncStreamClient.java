package com.netflix.client;

import java.util.concurrent.Future;

public interface AsyncStreamClient<T extends ClientRequest, S extends IResponse, U> {
    
    public interface StreamCallback<S, E> {
        public void onResponseReceived(S response);
        
        public void onError(Throwable e);
        
        public void onCompleted();
        
        public void onElement(E element);
    }
    
    public <E> Future<S> stream(T request, StreamDecoder<E, U> decooder, StreamCallback<S, E> callback) throws Exception;
}
