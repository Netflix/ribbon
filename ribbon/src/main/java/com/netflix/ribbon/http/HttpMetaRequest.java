/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon.http;

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
import rx.subjects.Subject;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.RequestWithMetaData;
import com.netflix.ribbon.RibbonResponse;

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

    private final HttpRequest<T> request;

    HttpMetaRequest(HttpRequest<T> request) {
        this.request = request;
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> observe() {
        RibbonHystrixObservableCommand<T> hystrixCommand = request.createHystrixCommand();
        final Observable<T> output = hystrixCommand.observe();
        return convertToRibbonResponse(output, hystrixCommand);
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> toObservable() {
        RibbonHystrixObservableCommand<T> hystrixCommand = request.createHystrixCommand();
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
        final RibbonHystrixObservableCommand<T> hystrixCommand = request.createHystrixCommand();
        final Future<T> f = hystrixCommand.getObservable().toBlocking().toFuture();
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
        RibbonHystrixObservableCommand<T> hystrixCommand = request.createHystrixCommand();
        T obj = hystrixCommand.getObservable().toBlocking().last();
        return new HttpMetaResponse<T>(obj, hystrixCommand);
    }    
}
