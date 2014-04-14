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
 * A simple callback that is only invoked when full response is received and buffered
 * from asynchronous communication.
 * 
 * @author awang
 *
 * @param <T> Type of the response, which is protocol specific.
 * 
 */
public abstract class BufferedResponseCallback<T extends IResponse> implements ResponseCallback<T, Object>{
    /**
     * This method does nothing. Subclass can override it to receive callback
     * when the initial response (for example, status code and headers of HTTP response) is received.
     */
    @Override
    public void responseReceived(T response) {
    }

    /**
     * This method does nothing as it is only intended for callbacks on partial content.
     */
    @Override
    public final void contentReceived(Object content) {
    }
}
