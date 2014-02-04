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
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.HttpClient;
import io.reactivex.netty.protocol.http.HttpClientBuilder;
import io.reactivex.netty.protocol.http.ObservableHttpResponse;
import io.reactivex.netty.protocol.text.sse.SSEEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

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
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.Serializer;
import com.netflix.serialization.TypeDef;
import com.sun.istack.internal.Nullable;

public class NettyHttpClient {

    private SerializationFactory<HttpSerializationContext> serializationFactory;
    private int connectTimeout;
    private int readTimeout;
    private IClientConfig config;
    private Bootstrap bootStrap;
    
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
        this.connectTimeout = config.getPropertyAsInteger(CommonClientConfigKey.ConnectTimeout, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        this.readTimeout = config.getPropertyAsInteger(CommonClientConfigKey.ReadTimeout, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);  
        this.serializationFactory = (serializationFactory == null) ? new JacksonSerializationFactory() : serializationFactory;
        this.bootStrap = (bootStrap == null) ? new Bootstrap().group(new NioEventLoopGroup()) : bootStrap;
    }
    
    protected EventLoopGroup getNextEventGroupLoop() {
        return bootStrap.group().next();
    }
    
    private static class SingleEntityConfigurator<T> implements PipelineConfigurator<T, FullHttpRequest> {
        private HttpRequest request;
        private TypeDef<T> typeDef;
        private SerializationFactory<HttpSerializationContext> serializationFactory;
        
        private final PipelineConfigurator<FullHttpResponse, FullHttpRequest> configurator;

        public SingleEntityConfigurator(PipelineConfigurator<FullHttpResponse, FullHttpRequest> pipelineConfigurator,
                HttpRequest request, SerializationFactory<HttpSerializationContext> serializationFactory, TypeDef<T> typeDef) {
            this.configurator = pipelineConfigurator;
            this.request = request;
            this.serializationFactory = serializationFactory;
            this.typeDef = typeDef;
        }

        @Override
        public void configureNewPipeline(ChannelPipeline pipeline) {
            configurator.configureNewPipeline(pipeline);
            pipeline.addLast(FullHttpResponseHandler.NAME, new FullHttpResponseHandler<T>(serializationFactory, request, typeDef));
        }
    }
        
    private FullHttpRequest getHttpRequest(HttpRequest request) throws ClientException {
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
            } else {
                Serializer serializer = null;
                if (request.getOverrideConfig() != null) {
                    serializer = request.getOverrideConfig().getPropertyWithType(CommonClientConfigKey.Serializer);
                }
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
    
    public <T> Observable<ServerSentEvent<T>> createServerSentEventEntityObservable(final HttpRequest request, final TypeDef<T> typeDef) {
        Observable<ObservableHttpResponse<SSEEvent>> observable = createServerSentEventObservable(request);
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
                                final Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, new NettyHttpHeaders(response), typeDef, serializationFactory);
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

    public Observable<ObservableHttpResponse<SSEEvent>> createServerSentEventObservable(final HttpRequest request) {
        return createObservableHttpResponse(request, PipelineConfigurators.sseClientConfigurator(), RxClient.ClientConfig.DEFAULT_CONFIG);
    }
    
    public Observable<HttpResponse> createFullHttpResponseObservable(final HttpRequest request) {
        return createEntityObservable(request, TypeDef.fromClass(HttpResponse.class));
    }
    
    public <T> Observable<T> createEntityObservable(final HttpRequest request, TypeDef<T> typeDef) {
        RxClient.ClientConfig clientConfig = new RxClient.ClientConfig.Builder(RxClient.ClientConfig.DEFAULT_CONFIG)
        .readTimeout(readTimeout, TimeUnit.MILLISECONDS).build();

        Observable<ObservableHttpResponse<T>> observableHttpResponse = createObservableHttpResponse(
                request, new SingleEntityConfigurator<T>(PipelineConfigurators.fullHttpMessageClientConfigurator(), request, serializationFactory, typeDef), clientConfig);
        return observableHttpResponse.flatMap(new Func1<ObservableHttpResponse<T>, Observable<T>>() {
            @Override
            public Observable<T> call(ObservableHttpResponse<T> t1) {
                return t1.content();
            }
        });
    }    

    
    private <T> T getProperty(IClientConfigKey<T> key, IClientConfig overrideConfig) {
        T value = null;
        if (overrideConfig != null) {
            value = overrideConfig.getPropertyWithType(key);
        }
        if (value == null) {
            value = getConfig().getPropertyWithType(key);
        }
        return value;
    }
        
    public <T> Observable<ObservableHttpResponse<T>> createObservableHttpResponse(final HttpRequest request, 
            final PipelineConfigurator<T, FullHttpRequest> protocolHandler, final RxClient.ClientConfig clientConfig) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(protocolHandler);
        Preconditions.checkNotNull(clientConfig);
        HttpClientBuilder<FullHttpRequest, T> clientBuilder =
                new HttpClientBuilder<FullHttpRequest, T>(request.getUri().getHost(), request.getUri().getPort());
        HttpClient<FullHttpRequest, T> client = clientBuilder.channelOption(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout).eventloop(getNextEventGroupLoop())
                .pipelineConfigurator(protocolHandler).config(clientConfig).build();
                
        FullHttpRequest r = null;
        try {
            r = getHttpRequest(request);
        } catch (final Exception e) {
            return Observable.error(e);
        }
        return client.submit(r);
    }   
    
    public <T> Subscription observeEntity(HttpRequest request, TypeDef<T> entityType, Observer<T> observer) {
        Preconditions.checkNotNull(observer);
        return createEntityObservable(request, entityType).subscribe(observer);
    }
    
    public Subscription observeHttpResponse(HttpRequest request, Observer<HttpResponse> observer) {
        Preconditions.checkNotNull(observer);
        return createFullHttpResponseObservable(request).subscribe(observer);
    }
    
    public Subscription observeServerSentEvent(HttpRequest request, Observer<? super SSEEvent> observer) {
        Preconditions.checkNotNull(observer);
        return createServerSentEventObservable(request).flatMap(new Func1<ObservableHttpResponse<SSEEvent>, Observable<SSEEvent>>() {
            @Override
            public Observable<SSEEvent> call(ObservableHttpResponse<SSEEvent> t1) {
                return t1.content();
            }
            
        }).subscribe(observer);
    }

    public <T> Subscription observeServerSentEventEntity(HttpRequest request, TypeDef<T> entityType, Observer<ServerSentEvent<T>> observer) {
        Preconditions.checkNotNull(observer);
        return createServerSentEventEntityObservable(request, entityType)
                .subscribe(observer);
    }
    
    public <T> T execute(HttpRequest request, TypeDef<T> responseEntityType) throws Exception {
        return createEntityObservable(request, responseEntityType).toBlockingObservable().last();
    }
    
    public HttpResponse execute(HttpRequest request) throws Exception {
        return createFullHttpResponseObservable(request).toBlockingObservable().last();
    }
}
