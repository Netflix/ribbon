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
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

import com.netflix.serialization.Deserializer;
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
