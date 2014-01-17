package com.netflix.client.netty.http;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;

import java.util.List;

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

public class HttpEntityDecoder<T> extends MessageToMessageDecoder<FullHttpResponse> {

    private TypeDef<T> type;
    private SerializationFactory<HttpSerializationContext> serializationFactory;
    private HttpRequest request;
    
    public HttpEntityDecoder(SerializationFactory<HttpSerializationContext> serializationFactory, HttpRequest request, TypeDef<T> type) {
        this.serializationFactory = serializationFactory;
        this.type = type;
        this.request = request;
    }
    
    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse msg,
            List<Object> out) throws Exception {
        int statusCode = msg.getStatus().code();
        if (type.getRawType().isAssignableFrom(HttpResponse.class)) {
            msg.content().retain();
            out.add(new NettyHttpResponse(msg, msg.content(), serializationFactory, request));
        } else if (statusCode >= 200 && statusCode < 300) {
            if (msg.content().isReadable()) {
                HttpHeaders headers = new NettyHttpHeaders(msg);
                Deserializer<T> deserializer = SerializationUtils.getDeserializer(request, headers, type, serializationFactory);
                if (deserializer == null) {
                    ctx.fireExceptionCaught(new ClientException("Unable to find appropriate deserializer for type " 
                            + type.getRawType() + ", and headers " + headers));
                    return;
                }
                try {
                    T obj = deserializer.deserialize(new ByteBufInputStream(msg.content()), type);
                    if (obj != null) {
                        out.add(obj);
                    }
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            }
        } else {
            // Specific entity is expected but status code is unexpected
            ctx.fireExceptionCaught(new UnexpectedHttpResponseException(new NettyHttpResponse(msg, msg.content(), serializationFactory, request)));
        }
    }
}
