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

package com.netflix.ribbonclientextensions.proxy.sample;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.ServerError;
import com.netflix.ribbonclientextensions.UnsuccessfulResponseException;
import com.netflix.ribbonclientextensions.http.HttpResponseValidator;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;

import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class HystrixHandlers {

    public static class SampleHttpResponseValidator implements HttpResponseValidator {

        @Override
        public void validate(HttpClientResponse<ByteBuf> response) throws UnsuccessfulResponseException, ServerError {
        }
    }

    public static class GenericFallbackHandler implements FallbackHandler<Object> {

        @Override
        public Observable<Object> getFallback(HystrixExecutableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
            return null;
        }
    }

    public static class MovieFallbackHandler implements FallbackHandler<Movie> {

        @Override
        public Observable<Movie> getFallback(HystrixExecutableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
            return null;
        }
    }
}
