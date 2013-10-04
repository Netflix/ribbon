package com.netflix.client;

public interface ResponseCallback<T extends IResponse, E> {
    public void completed(T response);

    public void failed(Throwable e);
    
    public void cancelled();
    
    public void responseReceived(T response);

    public void contentReceived(E content);    
}
