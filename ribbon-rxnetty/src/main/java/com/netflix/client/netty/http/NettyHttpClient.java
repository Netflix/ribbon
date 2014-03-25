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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCounted;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.client.pool.ChannelPool;
import io.reactivex.netty.client.pool.DefaultChannelPool;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfiguratorComposite;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.HttpObjectAggregationConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientPipelineConfigurator;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import rx.Observable;
import rx.Observer;
import rx.functions.Func1;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.http.UnexpectedHttpResponseException;
import com.netflix.loadbalancer.Server;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.TypeDef;
//import com.netflix.client.http.HttpResponse;

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
public class NettyHttpClient implements Closeable {

    private SerializationFactory<HttpSerializationContext> serializationFactory;
    private IClientConfig config;
    private ChannelPool channelPool = new DefaultChannelPool(
            DefaultClientConfigImpl.DEFAULT_MAX_TOTAL_HTTP_CONNECTIONS, DefaultClientConfigImpl.DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS);
 
    
    public static final IClientConfigKey<Boolean> AutoRetainResponse = new CommonClientConfigKey<Boolean>("AutoRetainResponse"){};
    
    public static PipelineConfigurator debugHandler = new PipelineConfigurator() {
        @Override
        public void configureNewPipeline(ChannelPipeline pipeline) {
            pipeline.addFirst("debugger", new LoggingHandler(LogLevel.INFO));
            
        }
    }; 

    
    public NettyHttpClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues(), new JacksonSerializationFactory(), 
                new Bootstrap().group(new NioEventLoopGroup()));        
    }
    
    public NettyHttpClient(IClientConfig config) {
        this(config, new JacksonSerializationFactory(), new Bootstrap().group(new NioEventLoopGroup()));
    }
    
    
    public NettyHttpClient(IClientConfig config, @Nullable SerializationFactory<HttpSerializationContext> serializationFactory, 
            @Nullable Bootstrap bootStrap) {
        Preconditions.checkNotNull(config);
        this.config = config;
        this.serializationFactory = (serializationFactory == null) ? new JacksonSerializationFactory() : serializationFactory;
    }
        
    public IClientConfig getConfig() {
        return config;
    }

    public SerializationFactory<HttpSerializationContext> getSerializationFactory() {
        return serializationFactory;
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

    private void setHost(HttpClientRequest<?> request, String host) {
        request.getHeaders().set(HttpHeaders.Names.HOST, host);
    }

    /**
     * Create an Observable for entities parsed from raw Server-Sent-Event using the default {@link SerializationFactory} and 
     * client's default configuration.
     * 
     * @see class description of {@link NettyHttpClient} 
     * @param typeDef The runtime type of T
     * @param <T> type of each event 
     */
    /*
    public <T> Observable<ServerSentEvent<T>> createServerSentEventEntityObservable(final HttpRequest request, final TypeDef<T> typeDef) {
        return createServerSentEventEntityObservable(request, typeDef, null);
    } */

    /**
     * Create an Observable of {@link SSEEvent}.
     * 
     * @see class description of {@link NettyHttpClient}
     * @see #createServerSentEventObservable(HttpClientRequest, IClientConfig)
     */
    public <I> Observable<ServerSentEvent> createSSEEventObservable(String host, int port, final HttpClientRequest<I> request, IClientConfig requestConfig) {
        return createServerSentEventObservable(host, port, request, requestConfig).flatMap(new Func1<HttpClientResponse<ServerSentEvent>, Observable<ServerSentEvent>>() {
            @Override
            public Observable<ServerSentEvent> call(HttpClientResponse<ServerSentEvent> t1) {
                return t1.getContent();
            }
        });
    }
    
    /**
     * Create an Observable for {@link ServerSentEventWithEntity}, where you can get the typed entity. 
     * If a special deserializer is need to convert each event into T,
     * it can be set as part of passed-in {@link IClientConfig}. For example 
     * <pre>{@code
     *     
     *     requestConfig.setPropertyWithType(IClientConfigKey.CommonKeys.Deserializer, deserializer);
     *     ...
     *     createServerSentEventEntityObservable(request, typeDef, requestConfig);
     *     
     * }</pre>
     * 
     * @see class description of {@link NettyHttpClient} 
     * @param typeDef The runtime type of T
     * @param <T> type of each event 
     */
    public <I, T> Observable<ServerSentEventWithEntity<T>> createServerSentEventEntityObservable(String host, int port, final HttpClientRequest<I> request, 
            final TypeDef<T> typeDef, @Nullable final IClientConfig requestConfig) {
        Observable<HttpClientResponse<ServerSentEvent>> observable = createServerSentEventObservable(host, port, request, requestConfig);
        return observable.flatMap(new Func1<HttpClientResponse<ServerSentEvent>, Observable<ServerSentEventWithEntity<T>>>() {
            @Override
            public Observable<ServerSentEventWithEntity<T>> call(final HttpClientResponse<ServerSentEvent> observableResponse) {
                int status = observableResponse.getStatus().code();
                if (status < 200 && status >= 300) {
                    return Observable.error(new ClientException("Unexpected response status: " + status));
                }
                final Deserializer<T> deserializer = requestConfig.getPropertyWithType(IClientConfigKey.CommonKeys.Deserializer);
                if (deserializer != null) {
                    return observableResponse.getContent().map(new Func1<ServerSentEvent, ServerSentEventWithEntity<T>>() {
                        @Override
                        public ServerSentEventWithEntity<T> call(ServerSentEvent t1) {
                            try {
                                return new ServerSentEventWithEntity<T>(t1.getEventId(), t1.getEventName(), 
                                        SerializationUtils.deserializeFromString(deserializer, t1.getEventData(), typeDef));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }  else {
                    return Observable.error(new ClientException("No appropriate deserializer"));
                }
            }
        });
    }

    /**
     * Create an Observable of {@link SSEEvent} along with {@link ObservableHttpResponse} using client's default configuration.
     * 
     * @see class description of {@link NettyHttpClient} 
     */
    public <I> Observable<HttpClientResponse<ServerSentEvent>> createServerSentEventObservable(String host, int port, final HttpClientRequest<I> request) {
        return createServerSentEventObservable(host, port, request, null);
    }

    /**
     * Create an Observable of {@link SSEEvent} along with {@link ObservableHttpResponse}
     * 
     * @see class description of {@link NettyHttpClient} 
     */
    public <I> Observable<HttpClientResponse<ServerSentEvent>> createServerSentEventObservable(String host, int port, final HttpClientRequest<I> request, @Nullable IClientConfig requestConfig) {
        return createObservableHttpResponse(host, port, request, PipelineConfigurators.<I>sseClientConfigurator(), requestConfig, null, RxClient.ClientConfig.DEFAULT_CONFIG);
    }

    /**
     * Create an Observable of {@link HttpClientResponse} using the client's default configuration.
     * 
     * @see class description of {@link NettyHttpClient}
     * @see #createFullHttpResponseObservable(HttpClientRequest, IClientConfig) 
     */
    public Observable<HttpClientResponse<ByteBuf>> createFullHttpResponseObservable(String host, int port, final HttpClientRequest<ByteBuf> request) {
        return createFullHttpResponseObservable(host, port, request, null);
    }
    
    /**
     * Create an Observable of {@link HttpClientResponse}.
     * <p/>
     * <b>Note: If you want to access the {@link HttpClientResponse} from a different thread other than the one that calls {@link Observer#onNext(Object)}, 
     * you need to cast the {@link HttpClientResponse} into {@link io.netty.util.ReferenceCounted} and call {@link ReferenceCounted#retain()} inside
     * {@link Observer#onNext(Object)}, in which
     * case you are also responsible to call {@link io.netty.util.ReferenceCounted#release()} when the reference is no longer needed. If 
     * the intention is to use {@link Observable#toBlockingObservable()} and blocks until the response is available, you should use {@link #execute(HttpClientRequest, IClientConfig)} where 
     * the retaining of the ByteBuf is automatically handled. 
     * 
     * @see class description of {@link NettyHttpClient} 
     */
    public <I> Observable<HttpClientResponse<ByteBuf>> createFullHttpResponseObservable(String host, int port, final HttpClientRequest<I> request, @Nullable IClientConfig requestConfig) {
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, requestConfig);
        RxClient.ClientConfig clientConfig = new RxClient.ClientConfig.Builder(RxClient.ClientConfig.DEFAULT_CONFIG)
        .readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS).build();
        return createObservableHttpResponse(host, port, request, PipelineConfigurators.<I,ByteBuf>httpClientConfigurator(), requestConfig, channelPool, clientConfig);
    }
    
    /**
     * Create an Observable of typed entity obtained from full http response using 
     * the client's default configuration and {@link SerializationFactory}.
     *
     * @see class description of {@link NettyHttpClient} 
     * @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    /* public <T> Observable<T> createEntityObservable(final HttpRequest request, TypeDef<T> typeDef) {
        return createEntityObservable(request, typeDef, null);
    } */
    
    /**
     * Create an Observable of typed entity obtained from full http response.
     * To use a custom {@link Deserializer}, you can set it with the passed-in {@link IClientConfig}. For example,
     * <pre>{@code
     *     requestConfig.setPropertyWithType(IClientConfigKey.CommonKeys.Deserializer, deserializer);
     * }</pre>
     *   
     * If an unexpected HTTP status code is received (less than 200
     * or greater than 300), {@link Observer#onError(Throwable)} will be called and the Throwable will be an instance of 
     * {@link UnexpectedHttpResponseException} 
     *
     * @see class description of {@link NettyHttpClient} 
     */
    public <I, O> Observable<O> createEntityObservable(String host, int port, final HttpClientRequest<I> request, final TypeDef<O> type, @Nullable final IClientConfig requestConfig) {
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, requestConfig);
        RxClient.ClientConfig clientConfig = new RxClient.ClientConfig.Builder(RxClient.ClientConfig.DEFAULT_CONFIG)
        .readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS).build();

        Observable<HttpClientResponse<ByteBuf>> observableHttpResponse = createObservableHttpResponse(host, port,
                request,  
                PipelineConfigurators.<I, ByteBuf>httpClientConfigurator(), 
                requestConfig,
                channelPool,
                clientConfig);
        return observableHttpResponse.flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<O>>() {
            Deserializer<O> deserializer = null;

            @Override
            public Observable<O> call(final HttpClientResponse<ByteBuf> t1) {
                int statusCode = t1.getStatus().code();
                if (statusCode >= 200 && statusCode < 300) {
                    NettyHttpHeaders headers = new NettyHttpHeaders(t1.getHeaders());
                    try {
                        deserializer = SerializationUtils.getDeserializer(new URI(request.getUri()), requestConfig, headers, type, serializationFactory);
                    } catch (URISyntaxException e) {
                        return Observable.error(e);
                    }
                    if (deserializer == null) {
                        return Observable.error(new ClientException("No appropriate deserializer found"));
                    }

                    return t1.getContent().map(new Func1<ByteBuf, O>() {
                        @Override
                        public O call(ByteBuf input) {
                            if (input.isReadable()) {
                                try {
                                    return deserializer.deserialize(new ByteBufInputStream(input), type);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                return null;
                            }
                        }

                    });
                } else {
                    return Observable.error(new UnexpectedHttpResponseException(statusCode, t1.getStatus().reasonPhrase()));
                }

            };
        });
    }

    /**
     * Create an Observable of ObservableHttpResponse. Use this API if you need to manipulate the Netty pipeline
     * and produce custom observable content.
     *  
     */
    public <I,O> Observable<HttpClientResponse<O>> createObservableHttpResponse(String host, int port, final HttpClientRequest<I> request, 
            final PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> configurator,
            final RxClient.ClientConfig rxClientConfig) {
        return createObservableHttpResponse(host, port, request, configurator, null, null, rxClientConfig);
    }
        
    /**
     * Create an Observable of ObservableHttpResponse. Use this API if you need to manipulate the Netty pipeline
     * and produce custom observable content.
     */
    <I,O> Observable<HttpClientResponse<O>> createObservableHttpResponse(String host, int port, final HttpClientRequest<I> request, 
            final  PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> configurator, 
            @Nullable final IClientConfig requestConfig, @Nullable ChannelPool channelPool,
            final RxClient.ClientConfig rxClientConfig) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(configurator);
        Preconditions.checkNotNull(rxClientConfig);
        
        PipelineConfigurator<HttpClientResponse<O>, HttpClientRequest<I>> composite 
            = new PipelineConfiguratorComposite<HttpClientResponse<O>, HttpClientRequest<I>>(configurator,
                debug);
        
        HttpClientBuilder<I, O> clientBuilder =
                new HttpClientBuilder<I, O>(host, port).pipelineConfigurator(configurator).channelPool(channelPool);
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, requestConfig);
        HttpClient<I, O> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout).config(rxClientConfig).build();
        setHost(request, host);
        return client.submit(request);
    }   

    public static PipelineConfigurator debug = new PipelineConfigurator() {
        @Override
        public void configureNewPipeline(ChannelPipeline pipeline) {
            pipeline.addFirst("debugger", new LoggingHandler(LogLevel.INFO));
            
        }
    }; 
    
    /**
     * Start asynchronous execution of the request with an {@link Observer} of the typed entity.
     * 
     * @param <T> type of the entity
     *  @see class description of {@link NettyHttpClient}
     *  @see #createEntityObservable(HttpClientRequest, TypeDef, IClientConfig)
     */
    /*
    public <I, O> Subscription observeEntity(String host, int port, HttpRequest<I> request, TypeDef<O> entityType, Observer<O> observer) {
        return observeEntity(host, port, request, entityType, observer, null);
    }
    */

    @Override
    public void close() {
    }
}
