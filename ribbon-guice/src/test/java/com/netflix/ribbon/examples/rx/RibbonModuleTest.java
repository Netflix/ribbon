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
package com.netflix.ribbon.examples.rx;


import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceFallbackHandler;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceResponseValidator;
import com.netflix.ribbon.examples.rx.common.RxMovieTransformer;
import com.netflix.ribbon.guice.RibbonModule;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.StringTransformer;
import org.junit.Test;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;


public class RibbonModuleTest {
    private static final int PORT = 7001;
    
    @Singleton
    public static class MyService extends AbstractRxMovieClient {
        private final HttpResourceGroup httpResourceGroup;
        private final HttpRequestTemplate<ByteBuf> registerMovieTemplate;
        private final HttpRequestTemplate<ByteBuf> updateRecommendationTemplate;
        private final HttpRequestTemplate<ByteBuf> recommendationsByUserIdTemplate;
        private final HttpRequestTemplate<ByteBuf> recommendationsByTemplate;

        @Inject
        public MyService(RibbonResourceFactory factory) {
            httpResourceGroup = factory.createHttpResourceGroup("movieServiceClient",
                    ClientOptions.create()
                            .withMaxAutoRetriesNextServer(3)
                            .withConfigurationBasedServerList("localhost:" + PORT));

            registerMovieTemplate = httpResourceGroup.newTemplateBuilder("registerMovie", ByteBuf.class)
                    .withMethod("POST")
                    .withUriTemplate("/movies")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withResponseValidator(new RecommendationServiceResponseValidator()).build();

            updateRecommendationTemplate = httpResourceGroup.newTemplateBuilder("updateRecommendation", ByteBuf.class)
                    .withMethod("POST")
                    .withUriTemplate("/users/{userId}/recommendations")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withResponseValidator(new RecommendationServiceResponseValidator()).build();

            recommendationsByUserIdTemplate = httpResourceGroup.newTemplateBuilder("recommendationsByUserId", ByteBuf.class)
                    .withMethod("GET")
                    .withUriTemplate("/users/{userId}/recommendations")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withFallbackProvider(new RecommendationServiceFallbackHandler())
                    .withResponseValidator(new RecommendationServiceResponseValidator()).build();

            recommendationsByTemplate = httpResourceGroup.newTemplateBuilder("recommendationsBy", ByteBuf.class)
                    .withMethod("GET")
                    .withUriTemplate("/recommendations?category={category}&ageGroup={ageGroup}")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withFallbackProvider(new RecommendationServiceFallbackHandler())
                    .withResponseValidator(new RecommendationServiceResponseValidator()).build();
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Observable<ByteBuf>[] triggerMoviesRegistration() {
            return new Observable[]{
                    registerMovieTemplate.requestBuilder()
                            .withRawContentSource(Observable.just(Movie.ORANGE_IS_THE_NEW_BLACK), new RxMovieTransformer())
                            .build().toObservable(),
                    registerMovieTemplate.requestBuilder()
                            .withRawContentSource(Observable.just(Movie.BREAKING_BAD), new RxMovieTransformer())
                            .build().toObservable(),
                    registerMovieTemplate.requestBuilder()
                            .withRawContentSource(Observable.just(Movie.HOUSE_OF_CARDS), new RxMovieTransformer())
                            .build().toObservable()
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Observable<ByteBuf>[] triggerRecommendationsUpdate() {
            return new Observable[]{
                    updateRecommendationTemplate.requestBuilder()
                            .withRawContentSource(Observable.just(Movie.ORANGE_IS_THE_NEW_BLACK.getId()), new StringTransformer())
                            .withRequestProperty("userId", TEST_USER)
                            .build().toObservable(),
                    updateRecommendationTemplate.requestBuilder()
                            .withRawContentSource(Observable.just(Movie.BREAKING_BAD.getId()), new StringTransformer())
                            .withRequestProperty("userId", TEST_USER)
                            .build().toObservable()
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Observable<ByteBuf>[] triggerRecommendationsSearch() {
            return new Observable[]{
                    recommendationsByUserIdTemplate.requestBuilder()
                            .withRequestProperty("userId", TEST_USER)
                            .build().toObservable(),
                    recommendationsByTemplate.requestBuilder()
                            .withRequestProperty("category", "Drama")
                            .withRequestProperty("ageGroup", "Adults")
                            .build().toObservable()
            };
        }
    }
    
    
    @Test
    public void shouldBind() {
        Injector injector = Guice.createInjector(
            new RibbonModule()
            );
            
        MyService service = injector.getInstance(MyService.class);
        service.runExample();
    }

}
