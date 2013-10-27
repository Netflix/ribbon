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

/**
 * Callback for asynchronous communication.
 * 
 * @author awang
 *
 * @param <T> Type of response, which is protocol specific
 * @param <E> Type of of object that can be formed from partial 
 *             content in the native stream. See {@link StreamDecoder}.
 */
public interface ResponseCallback<T extends IResponse, E> {
    /**
     * Invoked when all communications are successful and content is consumed.
     */
    public void completed(T response);

    /**
     * Invoked when any error happened in the communication or content consumption. 
     */
    public void failed(Throwable e);

    /**
     * Invoked if the I/O operation is cancelled after it is started.
     */
    public void cancelled();
    
    /**
     * Invoked when the initial response is received. For example, the status code and headers
     * of HTTP response is received.
     */
    public void responseReceived(T response);

    /**
     * Invoked when decoded content is delivered from {@link StreamDecoder}.
     */
    public void contentReceived(E content);    
}
