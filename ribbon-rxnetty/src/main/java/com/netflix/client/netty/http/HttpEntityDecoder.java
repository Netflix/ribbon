package com.netflix.client.netty.http;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.List;

import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.UnexpectedResponseException;
import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

public class HttpEntityDecoder<T> extends MessageToMessageDecoder<FullHttpResponse> {

    private TypeDef<T> type;
    private SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory;
    private HttpRequest request;
    
    public HttpEntityDecoder(SerializationFactory<ContentTypeBasedSerializerKey> serializationFactory, HttpRequest request, TypeDef<T> type) {
        this.serializationFactory = serializationFactory;
        this.type = type;
        this.request = request;
    }
    
    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpResponse msg,
            List<Object> out) throws Exception {
        Deserializer deserializer = null;
        if (request.getOverrideConfig() != null) {
            deserializer = request.getOverrideConfig().getTypedProperty(CommonClientConfigKey.Deserializer);
        }
        if (deserializer == null) {
            String contentType = msg.headers().get(HttpHeaders.Names.CONTENT_TYPE);
            deserializer = serializationFactory.getDeserializer(new ContentTypeBasedSerializerKey(contentType, type));
            if (deserializer == null) {
                ctx.fireExceptionCaught(new ClientException("Unable to find appropriate deserializer"));
            }
        }
        int statusCode = msg.getStatus().code();
        String reason = msg.getStatus().reasonPhrase();
        if (statusCode >= 200 && statusCode < 300) {
            T obj = deserializer.deserialize(new ByteBufInputStream(msg.content()), type);
            if (obj != null) {
                out.add(obj);
            }            
        } else {
            ctx.fireExceptionCaught(new UnexpectedResponseException(reason));
        }
    }
}
