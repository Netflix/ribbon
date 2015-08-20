package com.netflix.ribbon.transport.netty.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import com.netflix.client.ClientException;

public class DefaultResponseToErrorPolicy<O> implements Func2<HttpClientResponse<O>, Integer, Observable<HttpClientResponse<O>>> {
    @Override
    public Observable<HttpClientResponse<O>> call(HttpClientResponse<O> t1, Integer backoff) {
        if (t1.getStatus().equals(HttpResponseStatus.INTERNAL_SERVER_ERROR)) {
            return Observable.error(new ClientException(ClientException.ErrorType.GENERAL));
        }
        if (t1.getStatus().equals(HttpResponseStatus.SERVICE_UNAVAILABLE) ||
            t1.getStatus().equals(HttpResponseStatus.BAD_GATEWAY) ||
            t1.getStatus().equals(HttpResponseStatus.GATEWAY_TIMEOUT)) {
            if (backoff > 0) {
                return Observable.timer(backoff, TimeUnit.MILLISECONDS)
                            .concatMap(new Func1<Long, Observable<HttpClientResponse<O>>>() {
                                @Override
                                public Observable<HttpClientResponse<O>> call(Long t1) {
                                    return Observable.error(new ClientException(ClientException.ErrorType.SERVER_THROTTLED));
                                }
                            });
            }
            else {
                return Observable.error(new ClientException(ClientException.ErrorType.SERVER_THROTTLED));
            }
        }
        return Observable.just(t1);
    }
}
