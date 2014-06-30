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

package com.netflix.ribbonclientextensions.proxy;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.proxy.annotation.CacheProviders;
import com.netflix.ribbonclientextensions.proxy.annotation.CacheProviders.Provider;
import com.netflix.ribbonclientextensions.proxy.annotation.Content;
import com.netflix.ribbonclientextensions.proxy.annotation.ContentTransformerClass;
import com.netflix.ribbonclientextensions.proxy.annotation.EvCache;
import com.netflix.ribbonclientextensions.proxy.annotation.Http;
import com.netflix.ribbonclientextensions.proxy.annotation.Http.Header;
import com.netflix.ribbonclientextensions.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbonclientextensions.proxy.annotation.Hystrix;
import com.netflix.ribbonclientextensions.proxy.annotation.ResourceGroupSpec;
import com.netflix.ribbonclientextensions.proxy.annotation.TemplateName;
import com.netflix.ribbonclientextensions.proxy.annotation.Var;
import io.netty.buffer.ByteBuf;

/**
 * @author Tomasz Bak
 */
@ResourceGroupSpec(name = "movieServiceGroup")
public interface MovieService {

    @TemplateName("recommendationsByUserId")
    @Http(
            method = HttpMethod.GET,
            path = "/users/{userId}/recommendations",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc"),
            })
    @Hystrix(
            cacheKey = "userRecommendations/{userId}",
            validator = RecommendationServiceResponseValidator.class,
            fallbackHandler = RecommendationServiceFallbackHandler.class)
    @CacheProviders(@Provider(key = "{userId}", provider = InMemoryCacheProviderFactory.class))
    @EvCache(name = "recommendationsByUserId", appName = "recommendations", cacheKeyTemplate = "{userId}", ttl = 50,
            enableZoneFallback = true, transcoder = MovieEVCacheTranscoder.class)
    RibbonRequest<ByteBuf> recommendationsByUserId(@Var("userId") String userId);

    @TemplateName("recommendationsBy")
    @Http(
            method = HttpMethod.GET,
            path = "/recommendations?category={category}&ageGroup={ageGroup}",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc"),
            })
    @Hystrix(
            cacheKey = "{category},{ageGroup}",
            validator = RecommendationServiceResponseValidator.class,
            fallbackHandler = RecommendationServiceFallbackHandler.class)
    @CacheProviders(@Provider(key = "{category},{ageGroup}", provider = InMemoryCacheProviderFactory.class))
    @EvCache(name = "recommendations", appName = "recommendations", cacheKeyTemplate = "{category},{ageGroup}", ttl = 50,
            enableZoneFallback = true, transcoder = MovieEVCacheTranscoder.class)
    RibbonRequest<ByteBuf> recommendationsBy(@Var("category") String category, @Var("ageGroup") String ageGroup);

    @TemplateName("registerMovie")
    @Http(
            method = HttpMethod.POST,
            path = "/movies",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc"),
            })
    @Hystrix(validator = RecommendationServiceResponseValidator.class)
    @ContentTransformerClass(RxMovieTransformer.class)
    RibbonRequest<Void> registerMovie(@Content Movie movie);

    @TemplateName("updateRecommendations")
    @Http(
            method = HttpMethod.POST,
            path = "/users/{userId}/recommendations",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc"),
            })
    @Hystrix(validator = RecommendationServiceResponseValidator.class)
    RibbonRequest<Void> updateRecommendations(@Var("userId") String userId, @Var("movieId") String movieId);
}
