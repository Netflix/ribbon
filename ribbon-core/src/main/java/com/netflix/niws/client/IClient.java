package com.netflix.niws.client;


public interface IClient<T extends ClientRequest> {

    public IResponse execute(T task) throws Exception; 
}
