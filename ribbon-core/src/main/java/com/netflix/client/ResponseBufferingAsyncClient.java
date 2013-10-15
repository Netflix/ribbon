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
import java.util.concurrent.Future;

import com.netflix.serialization.SerializationFactory;

public interface ResponseBufferingAsyncClient<T extends ClientRequest, S extends IResponse, U> extends Closeable {
    public Future<S> execute(T request, BufferedResponseCallback<S> callback) throws ClientException;
    
    public Future<S> execute(T request) throws ClientException;
    
    public void addSerializationFactory(SerializationFactory<U> factory);
}
