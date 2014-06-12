package com.netflix.ribbonclientextensions;


public interface RibbonRequest<T> extends RxRequest<T> {

    public RibbonRequest<RibbonResponse<T>> withMetadata();
}
