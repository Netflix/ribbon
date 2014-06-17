package com.netflix.ribbonclientextensions.http;

import java.util.concurrent.Future;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.RibbonResponse;

class HttpRequest<I, O> implements RibbonRequest<O> {

    private RibbonHystrixObservableCommand<I, O> hystrixCommand;
    
    HttpRequest(RibbonHystrixObservableCommand<I, O> hystrixCommand) {
        this.hystrixCommand = hystrixCommand;
    }
    
    @Override
    public O execute() {
        return hystrixCommand.execute();
    }

    @Override
    public Future<O> queue() {
        return hystrixCommand.queue();
    }

    @Override
    public Observable<O> observe() {
        return hystrixCommand.observe();
    }

    @Override
    public Observable<O> toObservable() {
        return hystrixCommand.toObservable();
    }
    
    @Override
    public RibbonRequest<RibbonResponse<O>> withMetadata() {
        return new RibbonRequest<RibbonResponse<O>>() {

            @Override
            public RibbonResponse<O> execute() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Future<RibbonResponse<O>> queue() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Observable<RibbonResponse<O>> observe() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public Observable<RibbonResponse<O>> toObservable() {
                Observable<O> fromHystrix = HttpRequest.this.toObservable();
                // TODO: return the 
                return null;
               
            }

            @Override
            public RibbonRequest<RibbonResponse<RibbonResponse<O>>> withMetadata() {
                return null;
            }
        };
       
    }

}
