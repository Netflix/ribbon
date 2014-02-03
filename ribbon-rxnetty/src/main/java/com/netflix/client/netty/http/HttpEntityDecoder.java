package com.netflix.client.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
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

public class HttpEntityDecoder<T> extends ByteToMessageDecoder {

    public static final String NAME = "http-entity-decoder";
    
    private TypeDef<T> type;
    private Deserializer<T> deserializer;
    
    public HttpEntityDecoder(Deserializer<T> deserializer, TypeDef<T> type) {
        this.type = type;
        this.deserializer = deserializer;
    }
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
            List<Object> out) throws Exception {
        if (in.isReadable()) {
            try {
                T obj = deserializer.deserialize(new ByteBufInputStream(in), type);
                if (obj != null) {
                    out.add(obj);
                }
            } catch (Exception e) {
                ctx.fireExceptionCaught(e);
            }
        }
        
    }
}
