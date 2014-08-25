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

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.CacheProvider;
import com.netflix.ribbon.proxy.annotation.EvCache;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import com.netflix.ribbon.proxy.sample.EvCacheClasses.SampleEVCacheTranscoder;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.SampleHttpResponseValidator;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;
import io.netty.buffer.ByteBuf;

/**
 * @author Allen Wang
 */
public interface SampleMovieServiceWithEVCache extends SampleMovieService {
    @TemplateName("findMovieById")
    @Http(
            method = HttpMethod.GET,
            uri = "/movies/{id}",
            headers = {
                    @Header(name = "X-MyHeader1", value = "value1.1"),
                    @Header(name = "X-MyHeader1", value = "value1.2"),
                    @Header(name = "X-MyHeader2", value = "value2")
            })
    @Hystrix(
            cacheKey = "findMovieById/{id}",
            validator = SampleHttpResponseValidator.class,
            fallbackHandler = MovieFallbackHandler.class)
    @CacheProvider(key = "findMovieById_{id}", provider = SampleCacheProviderFactory.class)

    @EvCache(name = "movie-cache", appName = "movieService", key = "movie-{id}", ttl = 50,
            enableZoneFallback = true, transcoder = SampleEVCacheTranscoder.class)
    RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);
}
