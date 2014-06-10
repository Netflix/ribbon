package com.netflix.ribbonclientextensions;

public interface ClientResponse<T, R> {

    public T getData();
    
    public R getMetaData();
}
