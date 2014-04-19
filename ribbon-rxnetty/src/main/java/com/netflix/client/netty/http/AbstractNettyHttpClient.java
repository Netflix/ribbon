package com.netflix.client.netty.http;

import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpHeaders;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;

import javax.annotation.Nullable;

import rx.Observable;
import rx.functions.Func1;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.ClientException.ErrorType;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

public abstract class AbstractNettyHttpClient<O> {
    protected final IClientConfig config;
 
    public AbstractNettyHttpClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues());        
    }
    
    public AbstractNettyHttpClient(IClientConfig config) {
        Preconditions.checkNotNull(config);
        this.config = config;
    }

    public final IClientConfig getConfig() {
        return config;
    }

    protected <S> S getProperty(IClientConfigKey<S> key, @Nullable IClientConfig requestConfig) {
        if (requestConfig != null && requestConfig.getPropertyWithType(key) != null) {
            return requestConfig.getPropertyWithType(key);
        } else {
            return config.getPropertyWithType(key);
        }
    }

    protected void setHost(HttpClientRequest<?> request, String host) {
        request.getHeaders().set(HttpHeaders.Names.HOST, host);
    }

    protected abstract <I> HttpClient<I, O> getRxClient(String host, int port);
    
    protected <I> Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request) {
        return submit(host, port, request, null);
    }
       
    protected static <I> RepeatableContentHttpRequest<I> getRepeatableRequest(HttpClientRequest<I> original) {
        if (original instanceof RepeatableContentHttpRequest) {
            return (RepeatableContentHttpRequest<I>) original;
        }
        return new RepeatableContentHttpRequest<I>(original);
    }

    <I> Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request, @Nullable final IClientConfig requestConfig) {
        Preconditions.checkNotNull(request);
        HttpClient<I,O> rxClient = getRxClient(host, port);
        setHost(request, host);
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, requestConfig);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder().readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS);
        final RxClient.ClientConfig rxClientConfig = builder.build();
        return rxClient.submit(request, rxClientConfig).flatMap(new Func1<HttpClientResponse<O>, Observable<HttpClientResponse<O>>>() {
            @Override
            public Observable<HttpClientResponse<O>> call(
                    HttpClientResponse<O> t1) {
                if (t1.getStatus().code() == 503) {
                    return Observable.error(new ClientException(ErrorType.SERVER_THROTTLED, t1.getStatus().reasonPhrase()));
                } else {
                    return Observable.from(t1);
                }
            }
        });
    }   
}
