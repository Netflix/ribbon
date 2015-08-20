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

package com.netflix.ribbon.proxy.sample;

import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.ribbon.ServerError;
import com.netflix.ribbon.UnsuccessfulResponseException;
import com.netflix.ribbon.http.HttpResponseValidator;
import com.netflix.ribbon.hystrix.FallbackHandler;

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

    public static class MovieFallbackHandler implements FallbackHandler<Movie> {

        @Override
        public Observable<Movie> getFallback(HystrixInvokableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
            return null;
        }
    }
}
