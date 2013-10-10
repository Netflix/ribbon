package com.netflix.client;

public abstract class BufferedResponseCallback<T extends IResponse> implements ResponseCallback<T, Object>{
    @Override
    public void responseReceived(T response) {
    }

    @Override
    public final void contentReceived(Object content) {
    }
}
