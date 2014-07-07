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

package com.netflix.ribbon.examples.rx.template;

import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.examples.rx.AbstractRxMovieClient;
import com.netflix.ribbon.examples.rx.RxMovieServer;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceFallbackHandler;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceResponseValidator;
import com.netflix.ribbon.examples.rx.common.RxMovieTransformer;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.StringTransformer;
import rx.Observable;

/**
 * Run {@link com.netflix.ribbon.examples.rx.RxMovieServer} prior to runnng this example!
 *
 * @author Tomasz Bak
 */
public class RxMovieTemplateExample extends AbstractRxMovieClient {

    private final HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("movieServiceClient",
            ClientOptions.create()
                    .withMaxAutoRetriesNextServer(3)
                    .withConfigurationBasedServerList("localhost:" + RxMovieServer.DEFAULT_PORT));

    private final HttpRequestTemplate<Void> registerMovieTemplate = httpResourceGroup.newRequestTemplate("registerMovie", Void.class)
            .withMethod("POST")
            .withUriTemplate("/movies")
            .withHeader("X-Platform-Version", "xyz")
            .withHeader("X-Auth-Token", "abc")
            .withResponseValidator(new RecommendationServiceResponseValidator());

    private final HttpRequestTemplate<Void> updateRecommendationTemplate = httpResourceGroup.newRequestTemplate("updateRecommendation", Void.class)
            .withMethod("POST")
            .withUriTemplate("/users/{userId}/recommendations")
            .withHeader("X-Platform-Version", "xyz")
            .withHeader("X-Auth-Token", "abc")
            .withResponseValidator(new RecommendationServiceResponseValidator());

    private final HttpRequestTemplate<ByteBuf> recommendationsByUserIdTemplate = httpResourceGroup.newRequestTemplate("recommendationsByUserId", ByteBuf.class)
            .withMethod("GET")
            .withUriTemplate("/users/{userId}/recommendations")
            .withHeader("X-Platform-Version", "xyz")
            .withHeader("X-Auth-Token", "abc")
            .withFallbackProvider(new RecommendationServiceFallbackHandler())
            .withResponseValidator(new RecommendationServiceResponseValidator());

    private final HttpRequestTemplate<ByteBuf> recommendationsByTemplate = httpResourceGroup.newRequestTemplate("recommendationsBy", ByteBuf.class)
            .withMethod("GET")
            .withUriTemplate("/recommendations?category={category}&ageGroup={ageGroup}")
            .withHeader("X-Platform-Version", "xyz")
            .withHeader("X-Auth-Token", "abc")
            .withFallbackProvider(new RecommendationServiceFallbackHandler())
            .withResponseValidator(new RecommendationServiceResponseValidator());

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<Void>[] triggerMoviesRegistration() {
        return new Observable[]{
                registerMovieTemplate.requestBuilder()
                        .withRawContentSource(Observable.just(Movie.ORANGE_IS_THE_NEW_BLACK), new RxMovieTransformer())
                        .build().observe(),
                registerMovieTemplate.requestBuilder()
                        .withRawContentSource(Observable.just(Movie.BREAKING_BAD), new RxMovieTransformer())
                        .build().observe(),
                registerMovieTemplate.requestBuilder()
                        .withRawContentSource(Observable.just(Movie.HOUSE_OF_CARDS), new RxMovieTransformer())
                        .build().observe()
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<Void>[] triggerRecommendationsUpdate() {
        return new Observable[]{
                updateRecommendationTemplate.requestBuilder()
                        .withRawContentSource(Observable.just(Movie.ORANGE_IS_THE_NEW_BLACK.getId()), new StringTransformer())
                        .withRequestProperty("userId", TEST_USER)
                        .build().observe(),
                updateRecommendationTemplate.requestBuilder()
                        .withRawContentSource(Observable.just(Movie.BREAKING_BAD.getId()), new StringTransformer())
                        .withRequestProperty("userId", TEST_USER)
                        .build().observe()
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<ByteBuf>[] triggerRecommendationsSearch() {
        return new Observable[]{
                recommendationsByUserIdTemplate.requestBuilder()
                        .withRequestProperty("userId", TEST_USER)
                        .build().observe(),
                recommendationsByTemplate.requestBuilder()
                        .withRequestProperty("category", "Drama")
                        .withRequestProperty("ageGroup", "Adults")
                        .build().observe()
        };
    }

    public static void main(String[] args) {
        System.out.println("Starting templates based movie service...");
        new RxMovieTemplateExample().execute();
    }
}
