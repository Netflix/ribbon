package com.netflix.client.netty.http;

import com.netflix.client.ClientException;
import com.netflix.client.http.HttpHeaders;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.client.http.UnexpectedHttpResponseException;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.SerializationUtils;
import com.netflix.serialization.TypeDef;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;

public class FullHttpResponseHandler<T> extends ChannelInboundHandlerAdapter {

    public static final String NAME = "http-response-handler";
    
    private final HttpRequest request;
    private final SerializationFactory<HttpSerializationContext> serializationFactory;
    private final TypeDef<T> type;
    
    public FullHttpResponseHandler(SerializationFactory<HttpSerializationContext> serializationFactory, HttpRequest request, TypeDef<T> type) {
        this.request = request;
        this.serializationFactory = serializationFactory;
        this.type = type;
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            // send the headers down the pipeline first so that headers observers will get notified
            DefaultHttpResponse newResponse = new DefaultHttpResponse(response.getProtocolVersion(), response.getStatus());
            newResponse.headers().set(response.headers());
            ctx.fireChannelRead(newResponse);
            
            if (type.getRawType().isAssignableFrom(HttpResponse.class)) {
                ctx.fireChannelRead(new NettyHttpResponse(response, response.content(), serializationFactory, request));
            } else {
                int statusCode = response.getStatus().code();
                if (statusCode >= 200 && statusCode < 300) {
                    HttpHeaders headers = new NettyHttpHeaders(response);
                    Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, headers, type, serializationFactory);
                    if (deserializer == null) {
                        ctx.fireExceptionCaught(new ClientException("Unable to find appropriate deserializer for type " 
                                + type.getRawType() + ", and headers " + headers));
                    } else {
                        ctx.pipeline().addAfter(NAME, HttpEntityDecoder.NAME, new HttpEntityDecoder<T>(deserializer, type));
                        ByteBuf content = response.content();
                        ctx.fireChannelRead(content);
                    }
                } else {
                    // assume there is no need to further look into the content of the response,
                    // pass null as the content to ensure buffer will be released.
                    ctx.fireExceptionCaught(new UnexpectedHttpResponseException(new NettyHttpResponse(response, null, serializationFactory, request)));
                }
            }
            // mark the end of the content so that content observers will get OnCompleted() call
            ctx.fireChannelRead(new DefaultLastHttpContent());
        }
    }
}
