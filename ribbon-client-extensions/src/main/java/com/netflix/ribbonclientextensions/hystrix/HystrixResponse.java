package com.netflix.ribbonclientextensions.hystrix;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.AsyncRequest;

public interface HystrixResponse<T> extends AsyncRequest<T> {
    HystrixExecutableInfo<T> getHystrixInfo();   
}
