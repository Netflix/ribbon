package com.netflix.client.netty.http;

import io.netty.handler.codec.http.HttpHeaders;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import javax.annotation.Nullable;

import rx.Observable;
import rx.functions.Func1;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.ClientException.ErrorType;
import com.netflix.client.LoadBalancerExecutor;
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

    protected <S> S getProperty(IClientConfigKey<S> key, com.netflix.client.http.HttpRequest request, @Nullable IClientConfig requestConfig) {
        if (requestConfig != null && requestConfig.getPropertyWithType(key) != null) {
            return requestConfig.getPropertyWithType(key);
        } else if (request.getOverrideConfig() != null && request.getOverrideConfig().getPropertyWithType(key) != null) {
            return request.getOverrideConfig().getPropertyWithType(key);
        } else {
            return config.getPropertyWithType(key);
        }
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

    protected abstract <I> HttpClient<I, O> getRxClient(String host, int port, IClientConfig overrideConfig);
    
    protected <I> Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request) {
        return submit(host, port, request, null);
    }
        
    <I> Observable<HttpClientResponse<O>> submit(String host, int port, final HttpClientRequest<I> request, @Nullable final IClientConfig requestConfig) {
        Preconditions.checkNotNull(request);
        HttpClient<I,O> rxClient = getRxClient(host, port, requestConfig);
        setHost(request, host);
        return rxClient.submit(request).flatMap(new Func1<HttpClientResponse<O>, Observable<HttpClientResponse<O>>>() {
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
