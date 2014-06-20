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

class HttpMetaRequest<I, O> implements RequestWithMetaData<O> {

    private static class ResponseWithSubject<O> extends RibbonResponse<Observable<O>> {
        Subject<O, O> subject;
        HystrixExecutableInfo<?> info;
        
        public ResponseWithSubject(Subject<O, O> subject,
                HystrixExecutableInfo<?> info) {
            super();
            this.subject = subject;
            this.info = info;
        }

        @Override
        public Observable<O> content() {
            return subject;
        }

        @Override
        public HystrixExecutableInfo<?> getHystrixInfo() {
            return info;
        }        
    }

    private HttpRequestBuilder<I, O> requestBuilder;

    HttpMetaRequest(HttpRequestBuilder<I, O> requestBuilder ) {
        this.requestBuilder = requestBuilder;
    }

    @Override
    public Observable<RibbonResponse<Observable<O>>> observe() {
        RibbonHystrixObservableCommand<I, O> hystrixCommand = requestBuilder.createHystrixCommand();
        final Observable<O> output = hystrixCommand.observe();
        return convertToRibbonResponse(output, hystrixCommand);
    }

    @Override
    public Observable<RibbonResponse<Observable<O>>> toObservable() {
        RibbonHystrixObservableCommand<I, O> hystrixCommand = requestBuilder.createHystrixCommand();
        final Observable<O> output = hystrixCommand.observe();
        return convertToRibbonResponse(output, hystrixCommand);        
    }
    
    private Observable<RibbonResponse<Observable<O>>> convertToRibbonResponse(final Observable<O> content, final HystrixObservableCommand<O> hystrixCommand) {
        return Observable.<RibbonResponse<Observable<O>>>create(new OnSubscribe<RibbonResponse<Observable<O>>>() {
            @Override
            public void call(
                    final Subscriber<? super RibbonResponse<Observable<O>>> t1) {
                final Subject<O, O> subject = PublishSubject.<O>create();
                content.subscribe(new Observer<O>() {
                    AtomicBoolean first = new AtomicBoolean(true);                    
                    
                    void createRibbonResponseOnFirstInvocation() {
                        if (first.compareAndSet(true, false)) {
                            t1.onNext(new ResponseWithSubject<O>(subject, hystrixCommand));
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
                    public void onNext(O t) {
                        createRibbonResponseOnFirstInvocation();                        
                        subject.onNext(t);
                    }
                });
            }
        });
    }
    

    @Override
    public Future<RibbonResponse<O>> queue() {
        final RibbonHystrixObservableCommand<I, O> hystrixCommand = requestBuilder.createHystrixCommand();
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
        RibbonHystrixObservableCommand<I, O> hystrixCommand = requestBuilder.createHystrixCommand();
        O obj = hystrixCommand.execute();
        return new HttpMetaResponse<O>(obj, hystrixCommand);
    }    
}
