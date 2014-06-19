package com.netflix.ribbonclientextensions.http;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observables.GroupedObservable;
import rx.subjects.ReplaySubject;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.RequestWithMetaData;
import com.netflix.ribbonclientextensions.RibbonResponse;

class HttpMetaRequest<I, O> implements RequestWithMetaData<O> {

    static class HttpMetaResponse<O> extends RibbonResponse<O> {

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
    
    private RibbonHystrixObservableCommand<I, O> hystrixCommand;

    HttpMetaRequest(RibbonHystrixObservableCommand<I, O> hystrixCommand) {
        this.hystrixCommand = hystrixCommand;
    }

    @Override
    public Observable<RibbonResponse<Observable<O>>> observe() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observable<RibbonResponse<Observable<O>>> toObservable() {
        final Observable<O> output = hystrixCommand.toObservable();
        
        return output.nest().map(new Func1<Observable<O>, RibbonResponse<Observable<O>>>(){
            @Override
            public RibbonResponse<Observable<O>> call(final Observable<O> t1) {
                return new RibbonResponse<Observable<O>>() {
                    @Override
                    public Observable<O> content() {
                        return t1;
                    }
                    @Override
                    public HystrixExecutableInfo<?> getHystrixInfo() {
                        return hystrixCommand;
                    }
                };
            }
        }); 
    }

    @Override
    public Future<RibbonResponse<O>> queue() {
        final Future<O> f = hystrixCommand.queue();
        return new Future<RibbonResponse<O>>() {
            @Override
            public boolean cancel(boolean arg0) {
                return f.cancel(arg0);
            }

            @Override
            public RibbonResponse<O> get() throws InterruptedException,
                    ExecutionException {
                final O obj = f.get();
                return new HttpMetaResponse<O>(obj, hystrixCommand);
            }

            @Override
            public RibbonResponse<O> get(long arg0, TimeUnit arg1)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                final O obj = f.get(arg0, arg1);
                return new HttpMetaResponse<O>(obj, hystrixCommand);
            }

            @Override
            public boolean isCancelled() {
                return f.isCancelled();
            }

            @Override
            public boolean isDone() {
                return f.isDone();
            }
        };
    }

    @Override
    public RibbonResponse<O> execute() {
        O obj = hystrixCommand.execute();
        return new HttpMetaResponse<O>(obj, hystrixCommand);
    }
    
}
