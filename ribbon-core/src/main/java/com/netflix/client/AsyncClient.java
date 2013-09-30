package com.netflix.client;

public interface AsyncClient<Request extends ClientRequest, Response extends ResponseWithTypedEntity> {
    public void execute(Request request, ResponseCallback<Response> callback) throws ClientException;

}


