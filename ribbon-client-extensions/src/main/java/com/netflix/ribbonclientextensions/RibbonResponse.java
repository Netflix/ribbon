package com.netflix.ribbonclientextensions;


import com.netflix.hystrix.HystrixExecutableInfo;

public abstract class RibbonResponse<T> {
    public abstract T content();
    
    public abstract HystrixExecutableInfo<?> getHystrixInfo();   
}
