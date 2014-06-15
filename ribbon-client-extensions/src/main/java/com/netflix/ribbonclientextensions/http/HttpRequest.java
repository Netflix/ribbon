package com.netflix.ribbonclientextensions.http;

import java.util.concurrent.Future;

import rx.Observable;

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
        // TODO Auto-generated method stub
        return null;
    }

}
