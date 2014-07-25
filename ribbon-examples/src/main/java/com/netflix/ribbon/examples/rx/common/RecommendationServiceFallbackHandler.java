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

package com.netflix.ribbon.examples.rx.common;

import com.netflix.hystrix.HystrixExecutableInfo;
import com.netflix.ribbon.hystrix.FallbackHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import rx.Observable;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class RecommendationServiceFallbackHandler implements FallbackHandler<ByteBuf> {
    @Override
    public Observable<ByteBuf> getFallback(HystrixExecutableInfo<?> hystrixInfo, Map<String, Object> requestProperties) {
        byte[] bytes = Movie.ORANGE_IS_THE_NEW_BLACK.toString().getBytes(Charset.defaultCharset());
        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer(bytes.length);
        byteBuf.writeBytes(bytes);
        return Observable.just(byteBuf);
    }
}
