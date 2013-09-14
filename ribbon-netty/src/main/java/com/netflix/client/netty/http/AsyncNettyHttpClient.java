package com.netflix.client.netty.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.client.AsyncClient;
import com.netflix.client.ClientException;
import com.netflix.client.ResponseCallback;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.DefaultSerializationFactory;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.Serializer;

public class AsyncNettyHttpClient implements AsyncClient<NettyHttpRequest, NettyHttpResponse> {

    SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory = new DefaultSerializationFactory();
    Bootstrap b = new Bootstrap();
    
    private final String RIBBON_HANDLER = "ribbonHandler"; 
    private final String READ_TIMEOUT_HANDLER = "readTimeoutHandler"; 

    private ExecutorService executors;

    private int readTimeout;
    private int connectTimeout;
    
    public AsyncNettyHttpClient(IClientConfig config) {
        String serializationFactoryClass = config.getPropertyAsString(CommonClientConfigKey.SerializationFactoryClassName, null);
        if (serializationFactoryClass != null) {
            try {
                serializationFactory = (SerializationFactory<ContentTypeBasedSerializerKey>) Class.forName(serializationFactoryClass).newInstance();
            } catch (Exception e) {
            }
        }
        executors = new ThreadPoolExecutor(5, 20, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(100), new ThreadPoolExecutor.AbortPolicy());

        EventLoopGroup group = new NioEventLoopGroup();
        b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new Initializer());
        connectTimeout = config.getPropertyAsInteger(CommonClientConfigKey.ConnectTimeout, 2000);
        readTimeout = config.getPropertyAsInteger(CommonClientConfigKey.ReadTimeout, 2000);
        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        b.option(ChannelOption.SO_TIMEOUT, 2);
    }
    
    
    private class Initializer extends ChannelInitializer<SocketChannel> {
        
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();

            p.addLast("log", new LoggingHandler(LogLevel.INFO));
            p.addLast("codec", new HttpClientCodec());
            
            // Remove the following line if you don't want automatic content decompression.
            p.addLast("inflater", new HttpContentDecompressor());

            // Uncomment the following line if you don't want to handle HttpChunks.
            p.addLast("aggregator", new HttpObjectAggregator(1048576));
            
        }        
    }
    
    
    @Override
    public void execute(final NettyHttpRequest request,  final ResponseCallback<NettyHttpResponse> callback) throws ClientException {
        final URI uri = request.getUri();
        String scheme = uri.getScheme() == null? "http" : uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            if ("http".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("https".equalsIgnoreCase(scheme)) {
                port = 443;
            }
        }
        final HttpRequest nettyHttpRequest = getHttpRequest(request);
        // Channel ch = null;
        try {
            ChannelFuture future = b.connect(host, port);
            future.addListener(new ChannelFutureListener() {         
                @Override
                public void operationComplete(final ChannelFuture f) {
                    try {
                        // per Netty javadoc, it is recommended to use separate thread to handle channel future.
                        executors.submit(new Runnable() {
                            @Override
                            public void run() {
                                if (f.isCancelled()) {
                                } else if (!f.isSuccess()) {
                                    callback.onException(f.cause());
                                } else {
                                    final Channel ch = f.channel();
                                    final ChannelPipeline p = ch.pipeline();

                                    // only add read timeout after successful channel connection
                                    if (p.get(READ_TIMEOUT_HANDLER) != null) {
                                        p.remove(READ_TIMEOUT_HANDLER);
                                    }
                                    p.addLast(READ_TIMEOUT_HANDLER, new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS));

                                    if (p.get(RIBBON_HANDLER) != null) {
                                        p.remove(RIBBON_HANDLER);
                                    }

                                    p.addLast(RIBBON_HANDLER, new SimpleChannelInboundHandler<HttpObject>() {
                                        HttpResponse response;
                                        ByteBuf content;
                                        AtomicBoolean channelRead = new AtomicBoolean(false);

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx,
                                                HttpObject msg) throws Exception {
                                            channelRead.set(true);
                                            if (msg instanceof HttpResponse) {
                                                HttpResponse response = (HttpResponse) msg;
                                                this.response = response;
                                            }
                                            if (msg instanceof HttpContent) {
                                                HttpContent content = (HttpContent) msg;
                                                this.content = content.content();
                                                if (content instanceof LastHttpContent) {
                                                    NettyHttpResponse nettyResponse = new NettyHttpResponse(this.response, this.content, serializationFactory, uri);
                                                    callback.onResponseReceived(nettyResponse);
                                                }
                                            }
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                            if (channelRead.get() && (cause instanceof io.netty.handler.timeout.ReadTimeoutException)) {
                                                return;
                                            }
                                            callback.onException(cause);
                                        }

                                    });
                                    ch.writeAndFlush(nettyHttpRequest);
                                }
                            }
                        });
                    } catch (Throwable e) {
                        // this will be called if task submission is rejected
                        callback.onException(e);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static String getContentType(Map<String, Collection<String>> headers) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> entry: headers.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase("content-type")) {
                Collection<String> values = entry.getValue();
                if (values != null && values.size() > 0) {
                    return values.iterator().next();
                }
            }
        }
        return null;
    }
    
    private HttpRequest getHttpRequest(NettyHttpRequest request) throws ClientException {
        HttpRequest r = null;
        Object entity = request.getEntity();
        String uri = request.getUri().getRawPath();
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
            String contentType = getContentType(request.getHeaders());    
            ContentTypeBasedSerializerKey key = new ContentTypeBasedSerializerKey(contentType, entity.getClass());
            Serializer serializer = serializationFactory.getSerializer(key).orNull();
            if (serializer == null) {
                throw new ClientException("Unable to find serializer for " + key);
            }
            byte[] content;
            try {
                content = serializer.serialize(entity);
            } catch (IOException e) {
                throw new ClientException("Error serializing entity in request", e);
            }
            ByteBuf buf = Unpooled.wrappedBuffer(content);
            r = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getVerb().name()), uri, buf);
            r.headers().set(HttpHeaders.Names.CONTENT_LENGTH, content.length);
        } else {
            r = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(request.getVerb().name()), uri);
        }
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Collection<String>> entry: request.getHeaders().entrySet()) {
                String name = entry.getKey();
                Collection<String> values = entry.getValue();
                r.headers().set(name, values);
            }
        }
        
        return r;
    }
    
}