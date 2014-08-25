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

package com.netflix.ribbon.examples.rx.proxy;

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.examples.rx.common.InMemoryCacheProviderFactory;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceFallbackHandler;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceResponseValidator;
import com.netflix.ribbon.examples.rx.common.RxMovieTransformer;
import com.netflix.ribbon.proxy.annotation.CacheProvider;
import com.netflix.ribbon.proxy.annotation.ClientProperties;
import com.netflix.ribbon.proxy.annotation.ClientProperties.Property;
import com.netflix.ribbon.proxy.annotation.Content;
import com.netflix.ribbon.proxy.annotation.ContentTransformerClass;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import io.netty.buffer.ByteBuf;

/**
 * @author Tomasz Bak
 */
@ClientProperties(properties = {
        @Property(name="ReadTimeout", value="2000"),
        @Property(name="ConnectTimeout", value="1000"),
        @Property(name="MaxAutoRetriesNextServer", value="2")
}, exportToArchaius = true)
public interface MovieService {

    @TemplateName("recommendationsByUserId")
    @Http(
            method = HttpMethod.GET,
            uri = "/users/{userId}/recommendations",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc")
            })
    @Hystrix(
            validator = RecommendationServiceResponseValidator.class,
            fallbackHandler = RecommendationServiceFallbackHandler.class)
    @CacheProvider(key = "{userId}", provider = InMemoryCacheProviderFactory.class)
    RibbonRequest<ByteBuf> recommendationsByUserId(@Var("userId") String userId);

    @TemplateName("recommendationsBy")
    @Http(
            method = HttpMethod.GET,
            uri = "/recommendations?category={category}&ageGroup={ageGroup}",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc")
            })
    @Hystrix(
            validator = RecommendationServiceResponseValidator.class,
            fallbackHandler = RecommendationServiceFallbackHandler.class)
    @CacheProvider(key = "{category},{ageGroup}", provider = InMemoryCacheProviderFactory.class)
    RibbonRequest<ByteBuf> recommendationsBy(@Var("category") String category, @Var("ageGroup") String ageGroup);

    @TemplateName("registerMovie")
    @Http(
            method = HttpMethod.POST,
            uri = "/movies",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc")
            })
    @Hystrix(validator = RecommendationServiceResponseValidator.class)
    @ContentTransformerClass(RxMovieTransformer.class)
    RibbonRequest<ByteBuf> registerMovie(@Content Movie movie);

    @TemplateName("updateRecommendations")
    @Http(
            method = HttpMethod.POST,
            uri = "/users/{userId}/recommendations",
            headers = {
                    @Header(name = "X-Platform-Version", value = "xyz"),
                    @Header(name = "X-Auth-Token", value = "abc")
            })
    @Hystrix(validator = RecommendationServiceResponseValidator.class)
    RibbonRequest<ByteBuf> updateRecommendations(@Var("userId") String userId, @Content String movieId);
}
