package com.netflix.client;

public interface StreamResponseCallback<T extends IResponse, S> extends ResponseCallback<T> {
    public void onResponseReceived(T response);

    public void onContentReceived(S content);    
}
