package com.netflix.ribbonclientextensions.http;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.RibbonResponse;

class HttpMetaResponse<O> extends RibbonResponse<O> {

    private O content;
    private HystrixExecutableInfo<?> hystrixInfo;

    public HttpMetaResponse(O content, HystrixExecutableInfo<?> hystrixInfo) {
        this.content = content;
        this.hystrixInfo = hystrixInfo;
    }
    @Override
    public O content() {
        return content;
    }

    @Override
    public HystrixExecutableInfo<?> getHystrixInfo() {
        return hystrixInfo;
    }        
}
