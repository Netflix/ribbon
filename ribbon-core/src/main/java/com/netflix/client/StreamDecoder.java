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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A decoder that can decode entities from partial content in a response. 
 * 
 * @author awang
 *
 * @param <T> Type of entity
 * @param <S> Type of storage used for partial content. For example, {@link ByteBuffer}.
 */
public interface StreamDecoder<T, S> {
    /**
     * Decode from the input and create an entity. The client implementation should call this method in 
     * a loop until it returns null, which means no more stream entity can be created from the unconsumed input.
     * If there is any unconsumed input, client implementation should buffer and use it in conjunction with
     * the next available input, for example, an HTTP chunk. In other words, the decoder should not
     * have to buffer unconsumed input.
     * 
     * @param input input to read and create entity from
     * @return Entity created, or null if nothing can be created from the unconsumed input
     * @throws IOException
     */
    T decode(S input) throws IOException;
}
