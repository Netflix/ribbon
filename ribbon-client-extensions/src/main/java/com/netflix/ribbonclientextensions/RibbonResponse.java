package com.netflix.ribbonclientextensions;

import com.netflix.hystrix.HystrixExecutableInfo;

public interface RibbonResponse<T> extends RxRequest<T> {
    HystrixExecutableInfo<T> getHystrixInfo();   
}
