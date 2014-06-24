package com.netflix.ribbonclientextensions.http;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.Observer;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbonclientextensions.RequestWithMetaData;
import com.netflix.ribbonclientextensions.RibbonResponse;

class HttpMetaRequest<T> implements RequestWithMetaData<T> {

    private static class ResponseWithSubject<T> extends RibbonResponse<Observable<T>> {
        Subject<T, T> subject;
        HystrixExecutableInfo<?> info;
        
        public ResponseWithSubject(Subject<T, T> subject,
                HystrixExecutableInfo<?> info) {
            super();
            this.subject = subject;
            this.info = info;
        }

        @Override
        public Observable<T> content() {
            return subject;
        }

        @Override
        public HystrixExecutableInfo<?> getHystrixInfo() {
            return info;
        }        
    }

    private HttpRequestBuilder<T> requestBuilder;

    HttpMetaRequest(HttpRequestBuilder<T> requestBuilder ) {
        this.requestBuilder = requestBuilder;
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> observe() {
        RibbonHystrixObservableCommand<T> hystrixCommand = requestBuilder.createHystrixCommand();
        final Observable<T> output = hystrixCommand.observe();
        return convertToRibbonResponse(output, hystrixCommand);
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> toObservable() {
        RibbonHystrixObservableCommand<T> hystrixCommand = requestBuilder.createHystrixCommand();
        final Observable<T> output = hystrixCommand.observe();
        return convertToRibbonResponse(output, hystrixCommand);        
    }
    
    private Observable<RibbonResponse<Observable<T>>> convertToRibbonResponse(final Observable<T> content, final HystrixObservableCommand<T> hystrixCommand) {
        return Observable.<RibbonResponse<Observable<T>>>create(new OnSubscribe<RibbonResponse<Observable<T>>>() {
            @Override
            public void call(
                    final Subscriber<? super RibbonResponse<Observable<T>>> t1) {
                final Subject<T, T> subject = PublishSubject.<T>create();
                content.subscribe(new Observer<T>() {
                    AtomicBoolean first = new AtomicBoolean(true);                    
                    
                    void createRibbonResponseOnFirstInvocation() {
                        if (first.compareAndSet(true, false)) {
                            t1.onNext(new ResponseWithSubject<T>(subject, hystrixCommand));
                            t1.onCompleted();
                        }                        
                    }
                    
                    @Override
                    public void onCompleted() {
                        createRibbonResponseOnFirstInvocation();
                        subject.onCompleted();
                    }
                    @Override
                    public void onError(Throwable e) {
                        createRibbonResponseOnFirstInvocation();                        
                        subject.onError(e);
                    }
                    @Override
                    public void onNext(T t) {
                        createRibbonResponseOnFirstInvocation();                        
                        subject.onNext(t);
                    }
                });
            }
        });
    }
    

    @Override
    public Future<RibbonResponse<T>> queue() {
        final RibbonHystrixObservableCommand<T> hystrixCommand = requestBuilder.createHystrixCommand();
        final Future<T> f = hystrixCommand.queue();
        return new Future<RibbonResponse<T>>() {
            @Override
            public boolean cancel(boolean arg0) {
                return f.cancel(arg0);
            }

            @Override
            public RibbonResponse<T> get() throws InterruptedException,
                    ExecutionException {
                final T obj = f.get();
                return new HttpMetaResponse<T>(obj, hystrixCommand);
            }

            @Override
            public RibbonResponse<T> get(long arg0, TimeUnit arg1)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                final T obj = f.get(arg0, arg1);
                return new HttpMetaResponse<T>(obj, hystrixCommand);
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
    public RibbonResponse<T> execute() {
        RibbonHystrixObservableCommand<T> hystrixCommand = requestBuilder.createHystrixCommand();
        T obj = hystrixCommand.execute();
        return new HttpMetaResponse<T>(obj, hystrixCommand);
    }    
}
