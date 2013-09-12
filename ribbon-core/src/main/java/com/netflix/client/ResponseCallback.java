package com.netflix.client;

public interface ResponseCallback<R extends ResponseWithTypedEntity> {
    public void onResponseReceived(R response);

    public void onException(Throwable e);
}

