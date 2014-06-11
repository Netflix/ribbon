package com.netflix.ribbonclientextensions;

import com.netflix.ribbonclientextensions.hystrix.HystrixResponse;

public interface RibbonRequest<T> extends AsyncRequest<T> {

    public RibbonRequest<HystrixResponse<T>> withHystrixInfo();
}
