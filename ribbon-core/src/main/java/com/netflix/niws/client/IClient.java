package com.netflix.niws.client;


public interface IClient<S extends ClientRequest, T extends IResponse> {

    public T execute(S task) throws Exception; 
}
