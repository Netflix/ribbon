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

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbon.RequestWithMetaData;
import com.netflix.ribbon.RibbonResponse;
import com.netflix.ribbon.http.hystrix.HystrixNotification;
import io.netty.buffer.ByteBuf;
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
        HystrixExecutableInfo<?> info;

        public ResponseWithSubject(Subject<T, T> subject,
                                   HystrixExecutableInfo<?> info) {
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
    public Observable<RibbonResponse<Observable<T>>> toObservable() {
        return convertToRibbonResponse(request.createHystrixCommandChain().materializedNotificationObservable());
    }

    @Override
    public Observable<RibbonResponse<Observable<T>>> observe() {
        Observable<HystrixNotification<T>> notificationObservable = request
                .createHystrixCommandChain()
                .materializedNotificationObservable();
        notificationObservable = retainBufferIfNeeded(notificationObservable);
        ReplaySubject<HystrixNotification<T>> subject = ReplaySubject.create();
        notificationObservable.subscribe(subject);
        return convertToRibbonResponse(subject);
    }

    @Override
    public Future<RibbonResponse<T>> queue() {
        Observable<HystrixNotification<T>> notificationObservable = request.createHystrixCommandChain().toNotificationObservable();
        notificationObservable = retainBufferIfNeeded(notificationObservable);
        final Future<HystrixNotification<T>> f = notificationObservable.toBlocking().toFuture();
        return new Future<RibbonResponse<T>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return f.cancel(mayInterruptIfRunning);
            }

            @Override
            public RibbonResponse<T> get() throws InterruptedException,
                    ExecutionException {
                final HystrixNotification<T> notification = f.get();
                return new HttpMetaResponse<T>(notification.toFutureResult(), notification.getHystrixObservableCommand());
            }

            @Override
            public RibbonResponse<T> get(long timeout, TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                final HystrixNotification<T> notification = f.get(timeout, timeUnit);
                return new HttpMetaResponse<T>(notification.toFutureResult(), notification.getHystrixObservableCommand());
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

    private Observable<HystrixNotification<T>> retainBufferIfNeeded(Observable<HystrixNotification<T>> notificationObservable) {
        if (request.isByteBufResponse()) {
            notificationObservable = notificationObservable.flatMap(new Func1<HystrixNotification<T>, Observable<HystrixNotification<T>>>() {
                @Override
                public Observable<HystrixNotification<T>> call(HystrixNotification<T> notification) {
                    if (notification.isOnNext()) {
                        ((ByteBuf) notification.getValue()).retain();
                    }
                    return Observable.just(notification);
                }
            });
        }
        return notificationObservable;
    }

    private Observable<RibbonResponse<Observable<T>>> convertToRibbonResponse(
            final Observable<HystrixNotification<T>> hystrixNotificationObservable) {
        return Observable.create(new OnSubscribe<RibbonResponse<Observable<T>>>() {
            @Override
            public void call(
                    final Subscriber<? super RibbonResponse<Observable<T>>> t1) {
                final Subject<T, T> subject = ReplaySubject.create();
                hystrixNotificationObservable.subscribe(new Action1<HystrixNotification<T>>() {
                    AtomicBoolean first = new AtomicBoolean(true);

                    @Override
                    public void call(HystrixNotification<T> notification) {
                        if (first.compareAndSet(true, false)) {
                            t1.onNext(new ResponseWithSubject<T>(subject, notification.getHystrixObservableCommand()));
                            t1.onCompleted();
                        }
                        if (notification.isOnNext()) {
                            subject.onNext(notification.getValue());
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
