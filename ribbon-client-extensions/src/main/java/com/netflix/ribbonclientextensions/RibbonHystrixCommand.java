package com.netflix.ribbonclientextensions;

import rx.Observable;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixObservableCommand;

class RibbonHystrixCommand<T> extends HystrixObservableCommand<T> {

    public RibbonHystrixCommand(HystrixCommandGroupKey group) {
        super(group);
        // TODO Auto-generated constructor stub
    }

    public RibbonHystrixCommand(
            com.netflix.hystrix.HystrixObservableCommand.Setter setter) {
        super(setter);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected Observable<T> run() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected Observable<T> getFallback() {
        // TODO Auto-generated method stub
        return super.getFallback();
    }
    
    
}
