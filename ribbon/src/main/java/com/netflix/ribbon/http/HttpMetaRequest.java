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

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.RequestWithMetaData;
import com.netflix.ribbon.RibbonResponse;
import com.netflix.ribbon.hystrix.HystrixObservableCommandChain;
import com.netflix.ribbon.hystrix.ResultCommandPair;
import io.netty.buffer.ByteBuf;
import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

class HttpMetaRequest<T> implements RequestWithMetaData<T> {

    private static class ResponseWithSubject<T> extends RibbonResponse<Observable<T>> {
        Subject<T, T> subject;
        HystrixInvokableInfo<?> info;

        public ResponseWithSubject(Subject<T, T> subject,
                                   HystrixInvokableInfo<?> info) {
            this.subject = subject;
            this.info = info;
        }

        @Override
        public Observable<T> content() {
            return subject;
        }

        @Override
        public HystrixInvokableInfo<?> getHystrixInfo() {
            return info;
        }
    }

    private final HttpRequest<T> request;

    HttpMetaRequest(HttpRequest<T> request) {
        this.request = request;
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> toObservable() {
        HystrixObservableCommandChain<T> commandChain = request.createHystrixCommandChain();
        return convertToRibbonResponse(commandChain, commandChain.toResultCommandPairObservable());
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> observe() {
        HystrixObservableCommandChain<T> commandChain = request.createHystrixCommandChain();
        Observable<ResultCommandPair<T>> notificationObservable = commandChain.toResultCommandPairObservable();
        notificationObservable = retainBufferIfNeeded(notificationObservable);
        ReplaySubject<ResultCommandPair<T>> subject = ReplaySubject.create();
        notificationObservable.subscribe(subject);
        return convertToRibbonResponse(commandChain, subject);
    }

    @Override
    public Future<RibbonResponse<T>> queue() {
        Observable<ResultCommandPair<T>> resultObservable = request.createHystrixCommandChain().toResultCommandPairObservable();
        resultObservable = retainBufferIfNeeded(resultObservable);
        final Future<ResultCommandPair<T>> f = resultObservable.toBlocking().toFuture();
        return new Future<RibbonResponse<T>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return f.cancel(mayInterruptIfRunning);
            }

            @Override
            public RibbonResponse<T> get() throws InterruptedException,
                    ExecutionException {
                final ResultCommandPair<T> pair = f.get();
                return new HttpMetaResponse<T>(pair.getResult(), pair.getCommand());
            }

            @Override
            public RibbonResponse<T> get(long timeout, TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                final ResultCommandPair<T> pair = f.get(timeout, timeUnit);
                return new HttpMetaResponse<T>(pair.getResult(), pair.getCommand());
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
        RibbonResponse<Observable<T>> response = observe().toBlocking().last();
        return new HttpMetaResponse<T>(response.content().toBlocking().last(), response.getHystrixInfo());
    }

    private Observable<ResultCommandPair<T>> retainBufferIfNeeded(Observable<ResultCommandPair<T>> resultObservable) {
        if (request.isByteBufResponse()) {
            resultObservable = resultObservable.map(new Func1<ResultCommandPair<T>, ResultCommandPair<T>>() {
                @Override
                public ResultCommandPair<T> call(ResultCommandPair<T> pair) {
                    ((ByteBuf) pair.getResult()).retain();
                    return pair;
                }
            });
        }
        return resultObservable;
    }

    private Observable<RibbonResponse<Observable<T>>> convertToRibbonResponse(
            final HystrixObservableCommandChain<T> commandChain, final Observable<ResultCommandPair<T>> hystrixNotificationObservable) {
        return Observable.create(new OnSubscribe<RibbonResponse<Observable<T>>>() {
            @Override
            public void call(
                    final Subscriber<? super RibbonResponse<Observable<T>>> t1) {
                final Subject<T, T> subject = ReplaySubject.create();
                hystrixNotificationObservable.materialize().subscribe(new Action1<Notification<ResultCommandPair<T>>>() {
                    AtomicBoolean first = new AtomicBoolean(true);

                    @Override
                    public void call(Notification<ResultCommandPair<T>> notification) {
                        if (first.compareAndSet(true, false)) {
                            HystrixObservableCommand<T> command = notification.isOnError() ? commandChain.getLastCommand() : notification.getValue().getCommand();
                            t1.onNext(new ResponseWithSubject<T>(subject, command));
                            t1.onCompleted();
                        }
                        if (notification.isOnNext()) {
                            subject.onNext(notification.getValue().getResult());
                        } else if (notification.isOnCompleted()) {
                            subject.onCompleted();
                        } else { // onError
                            subject.onError(notification.getThrowable());
                        }
                    }
                });
            }
        });
    }
}
