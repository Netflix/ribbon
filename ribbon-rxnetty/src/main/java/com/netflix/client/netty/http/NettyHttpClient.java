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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.util.ReferenceCounted;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.HttpClient;
import io.reactivex.netty.protocol.http.HttpClientBuilder;
import io.reactivex.netty.protocol.http.ObservableHttpResponse;
import io.reactivex.netty.protocol.text.sse.SSEEvent;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.functions.Func1;

import com.google.common.base.Preconditions;
import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.UnexpectedHttpResponseException;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.Serializer;
import com.netflix.serialization.TypeDef;

/**
 * An HTTP client built on top of Netty and RxJava. The core APIs are
 * 
 *  <li>{@link #createEntityObservable(HttpRequest, TypeDef, IClientConfig)}
 *  <li>{@link #createFullHttpResponseObservable(HttpRequest, IClientConfig)}
 *  <li>{@link #createServerSentEventEntityObservable(HttpRequest, TypeDef, IClientConfig)}
 *  <li>{@link #createServerSentEventObservable(HttpRequest, IClientConfig)}
 *  <li>{@link #createObservableHttpResponse(HttpRequest, PipelineConfigurator, IClientConfig, io.reactivex.netty.client.RxClient.ClientConfig)}
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
    private Bootstrap bootStrap;
    
    public static final IClientConfigKey<Boolean> AutoRetainResponse = new CommonClientConfigKey<Boolean>("AutoRetainResponse"){};
    
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
        this.bootStrap = (bootStrap == null) ? new Bootstrap().group(new NioEventLoopGroup()) : bootStrap;
    }
    
    protected EventLoopGroup getNextEventGroupLoop() {
        return bootStrap.group().next();
    }
    
    private static class SingleEntityConfigurator<T> implements PipelineConfigurator<T, FullHttpRequest> {
        private final HttpRequest request;
        private final TypeDef<T> typeDef;
        private final SerializationFactory<HttpSerializationContext> serializationFactory;
        private final IClientConfig requestConfig;
        
        private final PipelineConfigurator<FullHttpResponse, FullHttpRequest> configurator;

        public SingleEntityConfigurator(PipelineConfigurator<FullHttpResponse, FullHttpRequest> pipelineConfigurator,
                HttpRequest request, IClientConfig requestConfig, SerializationFactory<HttpSerializationContext> serializationFactory, TypeDef<T> typeDef) {
            this.configurator = pipelineConfigurator;
            this.request = request;
            this.serializationFactory = serializationFactory;
            this.typeDef = typeDef;
            this.requestConfig = requestConfig;
        }

        @Override
        public void configureNewPipeline(ChannelPipeline pipeline) {
            configurator.configureNewPipeline(pipeline);
            pipeline.addLast(FullHttpResponseHandler.NAME, new FullHttpResponseHandler<T>(serializationFactory, request, typeDef, requestConfig));
        }
    }
        
    private FullHttpRequest getHttpRequest(HttpRequest request, IClientConfig requestConfig) throws ClientException {
        FullHttpRequest r = null;
        Object entity = request.getEntity();
        String uri = request.getUri().toString();
        if (request.getQueryParams() != null) {
            QueryStringEncoder encoder = new QueryStringEncoder(uri);
            for (Map.Entry<String, Collection<String>> entry: request.getQueryParams().entrySet()) {
                String name = entry.getKey();
                Collection<String> values = entry.getValue();
                for (String value: values) {
                    encoder.addParam(name, value);
                }
            }
            uri = encoder.toString();
        }
        if (entity != null) {
            ByteBuf buf = null;
            int contentLength = -1;
            if (entity instanceof ByteBuf) {
                buf = (ByteBuf) entity;
                contentLength = buf.readableBytes();
            } else {
                Serializer serializer = getProperty(IClientConfigKey.CommonKeys.Serializer, request, requestConfig);
                if (serializer == null) {
                    HttpSerializationContext key = new HttpSerializationContext(request.getHttpHeaders(), request.getUri());
                    serializer = serializationFactory.getSerializer(key, request.getEntityType());
                }
                if (serializer == null) {
                    throw new ClientException("Unable to find serializer");
                }
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                try {
                    serializer.serialize(bout, entity, request.getEntityType());
                } catch (IOException e) {
                    throw new ClientException("Error serializing entity in request", e);
                }
                byte[] content = bout.toByteArray();
                buf = Unpooled.wrappedBuffer(content);
                contentLength = content.length;
            }
            r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getVerb().name()), uri, buf);
            if (contentLength >= 0) {
                r.headers().set(HttpHeaders.Names.CONTENT_LENGTH, contentLength);
            }
        } else {
            r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getVerb().name()), uri);
        }
        if (request.getHttpHeaders() != null) {
            for (Entry<String, String> header: request.getHttpHeaders().getAllHeaders()) {
                r.headers().set(header.getKey(), header.getValue());
            }
        }
        if (request.getUri().getHost() != null) {
            r.headers().set(HttpHeaders.Names.HOST, request.getUri().getHost());
        }
        return r;
    }

    public IClientConfig getConfig() {
        return config;
    }

    public SerializationFactory<HttpSerializationContext> getSerializationFactory() {
        return serializationFactory;
    }

    protected <S> S getProperty(IClientConfigKey<S> key, HttpRequest request, @Nullable IClientConfig requestConfig) {
        if (requestConfig != null && requestConfig.getPropertyWithType(key) != null) {
            return requestConfig.getPropertyWithType(key);
        } else if (request.getOverrideConfig() != null && request.getOverrideConfig().getPropertyWithType(key) != null) {
            return request.getOverrideConfig().getPropertyWithType(key);
        } else {
            return config.getPropertyWithType(key);
        }
    }
    
    /**
     * Create an Observable for entities parsed from raw Server-Sent-Event using the default {@link SerializationFactory} and 
     * client's default configuration.
     * 
     * @see class description of {@link NettyHttpClient} 
     * @param typeDef The runtime type of T
     * @param <T> type of each event 
     */
    public <T> Observable<ServerSentEvent<T>> createServerSentEventEntityObservable(final HttpRequest request, final TypeDef<T> typeDef) {
        return createServerSentEventEntityObservable(request, typeDef, null);
    }

    /**
     * Create an Observable of {@link SSEEvent}.
     * 
     * @see class description of {@link NettyHttpClient}
     * @see #createServerSentEventObservable(HttpRequest, IClientConfig)
     */
    public <T> Observable<SSEEvent> createSSEEventObservable(final HttpRequest request, IClientConfig requestConfig) {
        return createServerSentEventObservable(request, requestConfig).flatMap(new Func1<ObservableHttpResponse<SSEEvent>, Observable<SSEEvent>>() {
            @Override
            public Observable<SSEEvent> call(ObservableHttpResponse<SSEEvent> t1) {
                return t1.content();
            }
        });
    }
    
    /**
     * Create an Observable for {@link ServerSentEvent}, where you can get the typed entity. 
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
    public <T> Observable<ServerSentEvent<T>> createServerSentEventEntityObservable(final HttpRequest request, 
            final TypeDef<T> typeDef, @Nullable final IClientConfig requestConfig) {
        Observable<ObservableHttpResponse<SSEEvent>> observable = createServerSentEventObservable(request, requestConfig);
        return observable.flatMap(new Func1<ObservableHttpResponse<SSEEvent>, Observable<ServerSentEvent<T>>>() {

            @Override
            public Observable<ServerSentEvent<T>> call(
                    final ObservableHttpResponse<SSEEvent> observableResponse) {
                Observable<io.netty.handler.codec.http.HttpResponse> headerObservable = observableResponse.header();
                return headerObservable.flatMap(new Func1<io.netty.handler.codec.http.HttpResponse, Observable<ServerSentEvent<T>>>() {

                    @Override
                    public Observable<ServerSentEvent<T>> call(
                            final io.netty.handler.codec.http.HttpResponse response) {
                        return observableResponse.content().map(new Func1<SSEEvent, ServerSentEvent<T>>() {

                            @Override
                            public ServerSentEvent<T> call(SSEEvent t1) {
                                final Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, requestConfig, new NettyHttpHeaders(response), typeDef, serializationFactory);
                                if (deserializer == null) {
                                    throw new RuntimeException("No suitable deserializer found");
                                }
                                try {
                                    return new ServerSentEvent<T>(t1.getEventId(), t1.getEventName(), 
                                            SerializationUtils.deserializeFromString(deserializer, t1.getEventData(), typeDef));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    throw new RuntimeException(e);
                                }
                            }
                            
                        });
                    }
                    
                });
            }
            
        });
    }

    /**
     * Create an Observable of {@link SSEEvent} along with {@link ObservableHttpResponse} using client's default configuration.
     * 
     * @see class description of {@link NettyHttpClient} 
     */
    public Observable<ObservableHttpResponse<SSEEvent>> createServerSentEventObservable(final HttpRequest request) {
        return createServerSentEventObservable(request, null);
    }

    /**
     * Create an Observable of {@link SSEEvent} along with {@link ObservableHttpResponse}
     * 
     * @see class description of {@link NettyHttpClient} 
     */
    public Observable<ObservableHttpResponse<SSEEvent>> createServerSentEventObservable(final HttpRequest request, @Nullable IClientConfig requestConfig) {
        return createObservableHttpResponse(request, PipelineConfigurators.sseClientConfigurator(), requestConfig, RxClient.ClientConfig.DEFAULT_CONFIG);
    }

    /**
     * Create an Observable of {@link HttpResponse} using the client's default configuration.
     * 
     * @see class description of {@link NettyHttpClient}
     * @see #createFullHttpResponseObservable(HttpRequest, IClientConfig) 
     */
    public Observable<HttpResponse> createFullHttpResponseObservable(final HttpRequest request) {
        return createFullHttpResponseObservable(request, null);
    }
    
    /**
     * Create an Observable of {@link HttpResponse}.
     * <p/>
     * <b>Note: If you want to access the {@link HttpResponse} from a different thread other than the one that calls {@link Observer#onNext(Object)}, 
     * you need to cast the {@link HttpResponse} into {@link io.netty.util.ReferenceCounted} and call {@link ReferenceCounted#retain()} inside
     * {@link Observer#onNext(Object)}, in which
     * case you are also responsible to call {@link io.netty.util.ReferenceCounted#release()} when the reference is no longer needed. If 
     * the intention is to use {@link Observable#toBlockingObservable()} and blocks until the response is available, you should use {@link #execute(HttpRequest, IClientConfig)} where 
     * the retaining of the ByteBuf is automatically handled. 
     * 
     * @see class description of {@link NettyHttpClient} 
     */
    public Observable<HttpResponse> createFullHttpResponseObservable(final HttpRequest request, @Nullable IClientConfig requestConfig) {
        return createEntityObservable(request, TypeDef.fromClass(HttpResponse.class), requestConfig);
    }
    
    /**
     * Create an Observable of typed entity obtained from full http response using 
     * the client's default configuration and {@link SerializationFactory}.
     *
     * @see class description of {@link NettyHttpClient} 
     * @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    public <T> Observable<T> createEntityObservable(final HttpRequest request, TypeDef<T> typeDef) {
        return createEntityObservable(request, typeDef, null);
    }
    
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
    public <T> Observable<T> createEntityObservable(final HttpRequest request, TypeDef<T> typeDef, @Nullable IClientConfig requestConfig) {
        int requestReadTimeout = getProperty(IClientConfigKey.CommonKeys.ReadTimeout, request, requestConfig);
        RxClient.ClientConfig clientConfig = new RxClient.ClientConfig.Builder(RxClient.ClientConfig.DEFAULT_CONFIG)
        .readTimeout(requestReadTimeout, TimeUnit.MILLISECONDS).build();

        Observable<ObservableHttpResponse<T>> observableHttpResponse = createObservableHttpResponse(
                request,  
                new SingleEntityConfigurator<T>(PipelineConfigurators.fullHttpMessageClientConfigurator(), request, requestConfig, serializationFactory, typeDef),
                requestConfig,
                clientConfig);
        return observableHttpResponse.flatMap(new Func1<ObservableHttpResponse<T>, Observable<T>>() {
            @Override
            public Observable<T> call(ObservableHttpResponse<T> t1) {
                return t1.content();
            }
        });
    }    

    /**
     * Create an Observable of ObservableHttpResponse. Use this API if you need to manipulate the Netty pipeline
     * and produce custom observable content.
     *  
     */
    public <T> Observable<ObservableHttpResponse<T>> createObservableHttpResponse(final HttpRequest request, 
            final PipelineConfigurator<T, FullHttpRequest> protocolHandler,
            final RxClient.ClientConfig rxClientConfig) {
        return createObservableHttpResponse(request, protocolHandler, null, rxClientConfig);
    }
    
    /**
     * Create an Observable of ObservableHttpResponse. Use this API if you need to manipulate the Netty pipeline
     * and produce custom observable content.
     */
    public <T> Observable<ObservableHttpResponse<T>> createObservableHttpResponse(final HttpRequest request, 
            final PipelineConfigurator<T, FullHttpRequest> protocolHandler, @Nullable final IClientConfig requestConfig, 
            final RxClient.ClientConfig rxClientConfig) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(protocolHandler);
        Preconditions.checkNotNull(rxClientConfig);
        HttpClientBuilder<FullHttpRequest, T> clientBuilder =
                new HttpClientBuilder<FullHttpRequest, T>(request.getUri().getHost(), request.getUri().getPort());
        int requestConnectTimeout = getProperty(IClientConfigKey.CommonKeys.ConnectTimeout, request, requestConfig);
        HttpClient<FullHttpRequest, T> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, requestConnectTimeout).eventloop(getNextEventGroupLoop())
                .pipelineConfigurator(protocolHandler).config(rxClientConfig).build();
                
        FullHttpRequest r = null;
        try {
            r = getHttpRequest(request, requestConfig);
        } catch (final Exception e) {
            return Observable.error(e);
        }
        return client.submit(r);
    }   

    /**
     * Start asynchronous execution of the request with an {@link Observer} of the typed entity.
     * 
     * @param <T> type of the entity
     *  @see class description of {@link NettyHttpClient}
     *  @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    public <T> Subscription observeEntity(HttpRequest request, TypeDef<T> entityType, Observer<T> observer) {
        return observeEntity(request, entityType, observer, null);
    }

    
    /**
     * Start asynchronous execution of the request with an {@link Observer} of the typed entity.
     * 
     * @param <T> type of the entity
     * @see class description of {@link NettyHttpClient}
     * @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    public <T> Subscription observeEntity(HttpRequest request, TypeDef<T> entityType, Observer<T> observer, @Nullable IClientConfig requestConfig) {
        Preconditions.checkNotNull(observer);
        return createEntityObservable(request, entityType, requestConfig).subscribe(observer);
    }

    /**
     * Start asynchronous execution of the request with an {@link Observer} of the {@link HttpResponse}.
     * 
     *  @see class description of {@link NettyHttpClient}
     *  @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    public Subscription observeHttpResponse(HttpRequest request, Observer<HttpResponse> observer) {
        return observeHttpResponse(request, observer, null);
    }
    
    /**
     * Start asynchronous execution of the request with an {@link Observer} of the {@link HttpResponse}.
     * 
     *  @see class description of {@link NettyHttpClient}
     *  @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    public Subscription observeHttpResponse(HttpRequest request, Observer<HttpResponse> observer, @Nullable IClientConfig requestConfig) {
        Preconditions.checkNotNull(observer);
        return createFullHttpResponseObservable(request, requestConfig).subscribe(observer);
    }

    /**
     * @see #observeServerSentEvent(HttpRequest, Observer, IClientConfig) 
     */
    public Subscription observeServerSentEvent(HttpRequest request,Observer<? super SSEEvent> observer) {
        return observeServerSentEvent(request, observer, null);
    }

    /**
     * Start an asynchronous execution of the request with the passed in {@link Observer} of {@link SSEEvent}. 
     * 
     * @see class description of {@link NettyHttpClient}
     * @see #createSSEEventObservable(HttpRequest, IClientConfig)
     */
    public Subscription observeServerSentEvent(HttpRequest request,Observer<? super SSEEvent> observer, @Nullable IClientConfig requestConfig) {
        Preconditions.checkNotNull(observer);
        return createSSEEventObservable(request, requestConfig).subscribe(observer);
    }

    /**
     * @see #observeServerSentEventEntity(HttpRequest, TypeDef, Observer, IClientConfig)
     * @see class description of {@link NettyHttpClient}
     */
    public <T> Subscription observeServerSentEventEntity(HttpRequest request, TypeDef<T> entityType, Observer<ServerSentEvent<T>> observer) {
        return observeServerSentEventEntity(request, entityType, observer, null);
    }
    
    /**
     * Start an asynchronous execution of request for Sever-Sent-Event with the passed in {@link Observer} of {@link ServerSentEvent}
     * 
     *  @param <T> type of the entity for each event
     *  @see class description of {@link NettyHttpClient}
     *  @see #createServerSentEventEntityObservable(HttpRequest, TypeDef, IClientConfig)
     */
    public <T> Subscription observeServerSentEventEntity(HttpRequest request, TypeDef<T> entityType, Observer<ServerSentEvent<T>> observer, @Nullable IClientConfig requestConfig) {
        Preconditions.checkNotNull(observer);
        return createServerSentEventEntityObservable(request, entityType, requestConfig)
                .subscribe(observer);
    }

    /**
     * @see #execute(HttpRequest, IClientConfig) 
     */
    public <T> T execute(HttpRequest request, TypeDef<T> responseEntityType) throws Exception {
        return execute(request, responseEntityType, null);
    }
    
    /**
     * Synchronously execute request and get a typed entity from HttpResponse. If the response code is unexpected,
     * an {@link UnexpectedHttpResponseException} wrapped by RuntimeException will be thrown.
     * <p/>
     * <b>Note: if T is {@link HttpResponse}, you must explicitly release resources from the {@link HttpResponse}
     * by calling {@link HttpResponse#close()} in a finally block to release references to Netty ByteBuf.</b>
     *
     * @throws Exception if any error occurs during the execution, which will be wrapped by RuntimeException.
     * @see #createEntityObservable(HttpRequest, TypeDef, IClientConfig) 
     */
    public <T> T execute(HttpRequest request, TypeDef<T> responseEntityType, @Nullable IClientConfig requestConfig) throws Exception {
        if (responseEntityType.getRawType().isAssignableFrom(HttpResponse.class)) {
            IClientConfig config = (requestConfig == null) ? DefaultClientConfigImpl.getEmptyConfig() : requestConfig;
            config.setPropertyWithType(AutoRetainResponse, true);
            return createEntityObservable(request, responseEntityType, config).toBlockingObservable().last();
        } else {
            return createEntityObservable(request, responseEntityType, requestConfig).toBlockingObservable().last();
        }
    }

    /**
     * @see #execute(HttpRequest, IClientConfig)
     */
    public HttpResponse execute(HttpRequest request) throws Exception {
        return execute(request, (IClientConfig) null);
    }
    
    /**
     * Synchronously execute the request and get an {@link HttpResponse}
     * <p/>
     * <b>Note: You must explicitly release resources from the {@link HttpResponse}
     * by calling {@link HttpResponse#close()} in a finally block to release references to Netty ByteBuf.</b>
     * 
     * @throws Exception if any error occurs during the execution, which will be wrapped by RuntimeException.
     */
    public HttpResponse execute(HttpRequest request, @Nullable IClientConfig requestConfig) throws Exception {
        IClientConfig config = (requestConfig == null) ? DefaultClientConfigImpl.getEmptyConfig() : requestConfig;
        config.setPropertyWithType(AutoRetainResponse, true);
        return createFullHttpResponseObservable(request, config).toBlockingObservable().last();
    }

    @Override
    public void close() {
        bootStrap.group().shutdownGracefully();
    }
}
