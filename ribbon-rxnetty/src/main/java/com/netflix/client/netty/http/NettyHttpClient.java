/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.client.pool.ChannelPool;
import io.reactivex.netty.client.pool.DefaultChannelPool;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClient.HttpClientConfig;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Observer;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.netflix.client.ClientObservableProvider;
import com.netflix.client.LoadBalancerExecutor;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.Server;
import com.netflix.serialization.TypeDef;

/**
 * An HTTP client built on top of Netty and RxJava. The core APIs are
 * 
 *  <li>{@link #createEntityObservable(HttpClientRequest, TypeDef, IClientConfig)}
 *  <li>{@link #createFullHttpResponseObservable(HttpClientRequest, IClientConfig)}
 *  <li>{@link #createServerSentEventEntityObservable(HttpClientRequest, TypeDef, IClientConfig)}
 *  <li>{@link #createServerSentEventObservable(HttpClientRequest, IClientConfig)}
 *  <li>{@link #createObservableHttpResponse(HttpClientRequest, PipelineConfigurator, IClientConfig, io.reactivex.netty.client.RxClient.ClientConfig)}
 *  <br/><br/>
 *  <p/>
 *  These APIs return an {@link Observable}, but does not start execution of the request. Once an {@link Observer} is subscribed to the returned
 *  {@link Observable}, the execution will start asynchronously. Each subscription to the {@link Observable} will result in a new execution 
 *  of the request. Unsubscribing from the Observable will cancel the request. 
 *  Please consult <a href="http://netflix.github.io/RxJava/javadoc/rx/Observable.html">RxJava API</a> for details.  
 *  <br/><br/>
 *  The APIs starting with prefix "observe" are provided on top of the core APIs to allow the execution to start immediately and 
 *  asynchronously with a passed in {@link Observer}. This is basically the "callback" pattern.
 *  <br/><br/>
 *  The "execute" APIs are provided on top of the core APIs to offer synchronous call semantics. 
 *  <br/><br/>
 *  To support serialization and deserialization of entities in full Http response or Sever-Sent-Event stream, 
 *  a {@link SerializationFactory} which provides Jackson codec is installed
 *  by default. You can override the default serialization by passing in an {@link IClientConfig} with a {@link CommonClientConfigKey#Deserializer}
 *  and/or {@link CommonClientConfigKey#Serializer} property. You also need to pass in {@link TypeDef} object
 *  to hold the reference of the runtime entity type to overcome type erasure.
 *  <br/><br/>
 *  You may find {@link NettyHttpClientBuilder} is easier to create instances of {@link NettyHttpClient}.
 *  
 * @author awang
 *
 */
public class NettyHttpClient extends AbstractNettyHttpClient<ByteBuf> implements Closeable {

    private ChannelPool channelPool;
    
    public NettyHttpClient() {
        super();
    }
    
    public NettyHttpClient(IClientConfig config) {
        super(config);
        this.channelPool = createChannelPool(config);
    }
    
    private ChannelPool createChannelPool(IClientConfig config) {
        int maxTotalConnection = config.getPropertyWithType(IClientConfigKey.CommonKeys.MaxTotalHttpConnections,
                DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS);
        long timeoutValue = DefaultClientConfigImpl.DEFAULT_POOL_KEEP_ALIVE_TIME;
        TimeUnit unit = DefaultClientConfigImpl.DEFAULT_POOL_KEEP_ALIVE_TIME_UNITS;
        long poolTimeout = config.getPropertyWithType(IClientConfigKey.CommonKeys.PoolKeepAliveTime, -1);
        if (poolTimeout > 0) {
            timeoutValue = poolTimeout;
            String timeUnit = config.getPropertyWithType(IClientConfigKey.CommonKeys.PoolKeepAliveTimeUnits);
            if (Strings.isNullOrEmpty(timeUnit)) {
                throw new IllegalArgumentException("PoolKeepAliveTime is configured but PoolKeepAliveTimeUnits is missing");
            }
            unit = TimeUnit.valueOf(timeUnit);
        }
        long timeoutMiilis = TimeUnit.MILLISECONDS.convert(timeoutValue, unit);
        return new DefaultChannelPool(maxTotalConnection, timeoutMiilis);
    }
        
    @Override
    public void close() throws IOException {
    }

    @Override
    protected <I> HttpClient<I, ByteBuf> getRxClient(String host, int port, IClientConfig requestConfig) {
        HttpClientBuilder<I, ByteBuf> clientBuilder =
                new HttpClientBuilder<I, ByteBuf>(host, port).pipelineConfigurator(PipelineConfigurators.<I, ByteBuf>httpClientConfigurator());
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, requestConfig);
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, requestConfig);
        HttpClientConfig.Builder builder = new HttpClientConfig.Builder(HttpClientConfig.DEFAULT_CONFIG).followRedirect()
                .readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS);
        RxClient.ClientConfig rxClientConfig = builder.build();
        
        HttpClient<I, ByteBuf> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout)
                .config(rxClientConfig)
                .channelPool(channelPool)
                .build();
        return client;
    }

    @Override
    public <I> Observable<HttpClientResponse<ByteBuf>> submit(String host,
            int port, HttpClientRequest<I> request) {
        return super.submit(host, port, request);
    }

    @Override
    public <I> Observable<HttpClientResponse<ByteBuf>> submit(String host, int port,
            HttpClientRequest<I> request, IClientConfig requestConfig) {
        return super.submit(host, port, request, requestConfig);
    }
}
