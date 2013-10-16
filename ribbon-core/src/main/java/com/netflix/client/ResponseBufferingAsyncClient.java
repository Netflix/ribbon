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

import java.io.Closeable;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import com.netflix.serialization.SerializationFactory;

/**
 * Interface for asynchronous client that does simple request/response and
 * receives callbacks when the full content is buffered.  
 * 
 * @author awang
 *
 * @param <T> Request type
 * @param <S> Response type
 * @param <U> Implementation specific storage type for content. For example, {@link ByteBuffer} for Apache HttpAsyncClient
 *             and possibly {@link InputStream} for blocking I/O client 
 */
public interface ResponseBufferingAsyncClient<T extends ClientRequest, S extends IResponse, U> extends Closeable {
    /**
     * Asynchronously execute a request and receives a callback when the full content of the response is buffered.
     * 
     * @param request Request to execute
     * @param callback to be invoked
     * @return Future of the response
     * @throws ClientException If anything error happens when processing the request before the asynchronous call
     */
    public Future<S> execute(T request, BufferedResponseCallback<S> callback) throws ClientException;
    
    /**
     * Asynchronously execute a request and get future of the response.
     * 
     * @param request Request to execute
     * @return Future of the response
     * @throws ClientException If anything error happens when processing the request before the asynchronous call
     */
    public Future<S> execute(T request) throws ClientException;

    /**
     * Add a serialization provider for the client. {@link SerializationFactory} added last should
     * have the highest priority if multiple {@link SerializationFactory} can handle the same content.
     * 
     * @param factory
     */
    public void addSerializationFactory(SerializationFactory<U> factory);
}
