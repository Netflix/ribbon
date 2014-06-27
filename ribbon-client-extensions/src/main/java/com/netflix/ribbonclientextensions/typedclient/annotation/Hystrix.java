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
package com.netflix.ribbonclientextensions.typedclient.annotation;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbonclientextensions.http.HttpResponseValidator;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Hystrix {
    String cacheKey() default "";

    Class<? extends FallbackHandler<?>> fallbackHandler() default UndefFallbackHandler.class;

    Class<? extends HttpResponseValidator> validator() default UndefHttpResponseValidator.class;

    /**
     * Since null is not allowed as a default value in annotation, we need this marker class.
     */
    final class UndefFallbackHandler implements FallbackHandler<Object> {
        @Override
        public Observable<Object> getFallback(HystrixExecutableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
            return null;
        }
    }

    final class UndefHttpResponseValidator implements HttpResponseValidator {
        @Override
        public void validate(HttpClientResponse<ByteBuf> response) {

        }
    }
}
