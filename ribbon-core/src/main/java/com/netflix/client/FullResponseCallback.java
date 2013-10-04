package com.netflix.client;

public abstract class FullResponseCallback<T extends IResponse> implements ResponseCallback<T, Object>{
    @Override
    public void responseReceived(T response) {
    }

    @Override
    public void contentReceived(Object content) {
    }
}
