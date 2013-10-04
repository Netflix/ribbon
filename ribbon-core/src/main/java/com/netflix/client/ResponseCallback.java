package com.netflix.client;

public interface ResponseCallback<T extends IResponse> {
    public void completed(T response);

    public void failed(Throwable e);
    
    public void cancelled();
}

