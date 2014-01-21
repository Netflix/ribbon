package com.netflix.client.netty.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.netty.protocol.http.HttpProtocolHandler;
import rx.netty.protocol.http.HttpProtocolHandlerAdapter;
import rx.netty.protocol.http.Message;
import rx.netty.protocol.http.ObservableHttpClient;
import rx.netty.protocol.http.ObservableHttpResponse;
import rx.netty.protocol.http.SelfRemovingResponseTimeoutHandler;
import rx.netty.protocol.http.ValidatedFullHttpRequest;
import rx.util.functions.Func1;

import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.UnexpectedHttpResponseException;
import com.netflix.client.http.HttpRequest.Builder;
import com.netflix.client.http.HttpRequest.Verb;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.JacksonSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.Serializer;
import com.netflix.serialization.TypeDef;

public class RxNettyHttpClient {

    private ObservableHttpClient observableClient;
    private SerializationFactory<HttpSerializationContext> serializationFactory;
    private int connectTimeout;
    private int readTimeout;
    private IClientConfig config;
    
    public RxNettyHttpClient() {
        this(DefaultClientConfigImpl.getClientConfigWithDefaultValues(), new JacksonSerializationFactory(), 
                new Bootstrap().group(new NioEventLoopGroup()));        
    }
    
    public RxNettyHttpClient(IClientConfig config) {
        this(config, new JacksonSerializationFactory(), new Bootstrap().group(new NioEventLoopGroup()));
    }
    
    
    public RxNettyHttpClient(IClientConfig config, SerializationFactory<HttpSerializationContext> serializationFactory, 
            Bootstrap bootStrap) {
        this.config = config;
        this.connectTimeout = config.getPropertyAsInteger(CommonClientConfigKey.ConnectTimeout, DefaultClientConfigImpl.DEFAULT_CONNECT_TIMEOUT);
        this.readTimeout = config.getPropertyAsInteger(CommonClientConfigKey.ReadTimeout, DefaultClientConfigImpl.DEFAULT_READ_TIMEOUT);  
        this.serializationFactory = serializationFactory;
        this.observableClient = ObservableHttpClient.newBuilder()
                .withChannelOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .build(bootStrap.group());
    }
    
    private class SingleEntityHandler<T> extends HttpProtocolHandlerAdapter<T> {
        private HttpRequest request;
        private TypeDef<T> typeDef;
        
        private SingleEntityHandler(HttpRequest request, SerializationFactory<HttpSerializationContext> serializationFactory, TypeDef<T> typeDef) {
            this.request = request;
            this.typeDef = typeDef;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
            int timeout = readTimeout;
            if (request.getOverrideConfig() != null) {
                Integer overrideTimeout = request.getOverrideConfig().getPropertyWithType(CommonClientConfigKey.ReadTimeout);
                if (overrideTimeout != null) {
                    timeout = overrideTimeout.intValue();
                }
            }
            pipeline.addAfter("http-response-decoder", "http-aggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
            pipeline.addAfter("http-aggregator", SelfRemovingResponseTimeoutHandler.NAME, new SelfRemovingResponseTimeoutHandler(timeout, TimeUnit.MILLISECONDS));
            pipeline.addAfter(SelfRemovingResponseTimeoutHandler.NAME, "entity-decoder", new HttpEntityDecoder<T>(serializationFactory, request, typeDef));
        }
    }
        
    private ValidatedFullHttpRequest getHttpRequest(HttpRequest request) throws ClientException {
        ValidatedFullHttpRequest r = null;
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
            r = new ValidatedFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getVerb().name()), uri, buf);
            if (contentLength >= 0) {
                r.headers().set(HttpHeaders.Names.CONTENT_LENGTH, contentLength);
            }
        } else {
            r = new ValidatedFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getVerb().name()), uri);
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
        return createServerSentEventObservable(request)
                .flatMap(new Func1<ObservableHttpResponse<Message>, Observable<ServerSentEvent<T>>>() {
                    @Override
                    public Observable<ServerSentEvent<T>> call(
                            ObservableHttpResponse<Message> t1) {
                        io.netty.handler.codec.http.HttpResponse response = t1.response();
                        if (response.getStatus().code() != 200) {
                            return Observable.<ServerSentEvent<T>>error(new UnexpectedHttpResponseException(
                                    new NettyHttpResponse(response, null, serializationFactory, request)));
                        }
                        final Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, new NettyHttpHeaders(response), typeDef, serializationFactory);
                        return t1.content().map(new Func1<Message, ServerSentEvent<T>>() {
                            @Override
                            public ServerSentEvent<T> call(Message t1) {
                                try {
                                    return new ServerSentEvent<T>(t1.getEventId(), t1.getEventName(), 
                                            SerializationUtils.deserializeFromString(deserializer, t1.getEventData(), typeDef));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                });
    }

    public Observable<ObservableHttpResponse<Message>> createServerSentEventObservable(final HttpRequest request) {
        return createObservableHttpResponse(request, HttpProtocolHandler.SSE_HANDLER);
    }
    
    public Observable<HttpResponse> createFullHttpResponseObservable(final HttpRequest request) {
        return createEntityObservable(request, TypeDef.fromClass(HttpResponse.class));
    }
    
    
    public <T> Observable<T> createEntityObservable(final HttpRequest request, TypeDef<T> typeDef) {
        Observable<ObservableHttpResponse<T>> observableHttpResponse = createObservableHttpResponse(request, new SingleEntityHandler<T>(request, serializationFactory, typeDef));
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
        
    private static class RedirectException extends RuntimeException {
        public RedirectException(String message) {
            super(message);
        }
    }
    
    public <T> Observable<ObservableHttpResponse<T>> createObservableHttpResponse(final HttpRequest request, final HttpProtocolHandler<T> protocolHandler) {
        ValidatedFullHttpRequest r = null;
        try {
            r = getHttpRequest(request);
        } catch (final Exception e) {
            return Observable.error(e);
        }
        final Observable<ObservableHttpResponse<T>> observable = observableClient.execute(r, protocolHandler);
        Boolean followRedirect = getProperty(CommonClientConfigKey.FollowRedirects, request.getOverrideConfig());
        if (followRedirect != null && followRedirect.booleanValue()) {
            return observable.flatMap(new Func1<ObservableHttpResponse<T>, Observable<ObservableHttpResponse<T>>>() {
                @Override
                public Observable<ObservableHttpResponse<T>> call(
                        ObservableHttpResponse<T> t1) {
                    int statusCode = t1.response().getStatus().code();
                    switch (statusCode) {
                    case 301:
                    case 302:
                    case 303:
                    case 307:
                    case 308:
                        String location = t1.response().headers().get("Location");
                        if (location == null) {
                            return Observable.error(new Exception("Location header is not set in the redirect response"));
                        } 
                        
                        Builder builder = HttpRequest.newBuilder(request).uri(location);
                        if (statusCode == 303) {
                            // according to the spec, this must be done with GET
                            builder.verb(Verb.GET);
                        }
                        Observable<ObservableHttpResponse<T>> newObservable = createObservableHttpResponse(builder.build(), protocolHandler);
                        return newObservable;
                    default: break;
                    }
                    return Observable.from(t1);
                }
            });
        } else {
            return observable;
        }
    }    
}
