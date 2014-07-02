/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbon;

import io.reactivex.netty.protocol.http.client.HttpClientResponse;


/**
 * 
 * @author awang
 *
 * @param <T> Protocol specific response meta data, e.g., HttpClientResponse
 */
public interface ResponseValidator<T> {
    /**
     * @param response Protocol specific response object, e.g., {@link HttpClientResponse}
     * @throws UnsuccessfulResponseException throw if server is able to execute the request, but 
     *              returns an an unsuccessful response.
     *              For example, HTTP response with 404 status code. This will be treated as a valid
     *              response and will not trigger Hystrix fallback
     * @throws ServerError throw if the response indicates that there is an server error in executing the request. 
     *              For example, HTTP response with 500 status code. This will trigger Hystrix fallback.
     */
    public void validate(T response) throws UnsuccessfulResponseException, ServerError;
}
