/*
 *
 * Copyright 2013 Netflix, Inc.
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
package com.netflix.client;

import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import com.netflix.serialization.ContentTypeBasedSerializerKey;
import com.netflix.serialization.Deserializer;
import com.netflix.serialization.Serializer;

/**
 * Interface for asynchronous communication client with streaming capability.
 * 
 * @author awang
 *
 * @param <T> Request type
 * @param <S> Response type
 * @param <U> Type of storage used for delivering partial content, for example, {@link ByteBuffer}
 * @param <V> Type of key to find {@link Serializer} and {@link Deserializer} for the content. For example, for HTTP communication,
 *            the key type is {@link ContentTypeBasedSerializerKey}
 */
public interface AsyncClient<T extends ClientRequest, S extends IResponse, U, V> extends ResponseBufferingAsyncClient<T, S, V> {
    /**
     * Asynchronously execute a request.
     * 
     * @param request Request to execute
     * @param decooder Decoder to decode objects from the native stream 
     * @param callback Callback to be invoked when execution completes or fails
     * @return Future of the response
     * @param <E> Type of object to be decoded from the stream
     * 
     * @throws ClientException if exception happens before the actual asynchronous execution happens, for example, an error to serialize 
     *         the entity
     */
    public <E> Future<S> execute(T request, StreamDecoder<E, U> decooder, ResponseCallback<S, E> callback) throws ClientException;
}
