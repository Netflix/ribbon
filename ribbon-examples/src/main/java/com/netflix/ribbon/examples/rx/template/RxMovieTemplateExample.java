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

import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.ribbon.examples.rx.RxMovieServer;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceFallbackHandler;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceResponseValidator;
import com.netflix.ribbon.examples.rx.common.RxMovieTransformer;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.RawContentSource.SingletonRawSource;
import io.reactivex.netty.serialization.StringTransformer;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.String.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieTemplateExample {

    private static final String TEST_USER = "user1";
    private static final Pattern NEW_LINE_SPLIT_RE = Pattern.compile("\n");

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

    private boolean registerMovies() {
        System.out.print("Registering movies...");

        RibbonRequest<Void> requestOrange = registerMovieTemplate.requestBuilder()
                .withRawContentSource(new SingletonRawSource<Movie>(Movie.ORANGE_IS_THE_NEW_BLACK, new RxMovieTransformer()))
                .build();
        RibbonRequest<Void> requestBrakingBad = registerMovieTemplate.requestBuilder()
                .withRawContentSource(new SingletonRawSource<Movie>(Movie.BREAKING_BAD, new RxMovieTransformer()))
                .build();
        RibbonRequest<Void> requestHouseOfCards = registerMovieTemplate.requestBuilder()
                .withRawContentSource(new SingletonRawSource<Movie>(Movie.HOUSE_OF_CARDS, new RxMovieTransformer()))
                .build();

        Notification<Void> status = Observable.concat(
                requestOrange.observe(),
                requestBrakingBad.observe(),
                requestHouseOfCards.observe()).materialize().toBlocking().last();

        if (status.isOnError()) {
            System.err.println("ERROR: movie registration failure");
            status.getThrowable().printStackTrace();
            return false;
        }
        System.out.println("Movies registered.");
        return true;

    }

    private boolean updateRecommendations() {
        System.out.print("Updating recommendations for user " + TEST_USER + "...");

        RibbonRequest<Void> requestOrange = updateRecommendationTemplate.requestBuilder()
                .withRawContentSource(new SingletonRawSource<String>(Movie.ORANGE_IS_THE_NEW_BLACK.getId(), new StringTransformer()))
                .withRequestProperty("userId", TEST_USER)
                .build();
        RibbonRequest<Void> requestBreakingBad = updateRecommendationTemplate.requestBuilder()
                .withRawContentSource(new SingletonRawSource<String>(Movie.ORANGE_IS_THE_NEW_BLACK.getId(), new StringTransformer()))
                .withRequestProperty("userId", TEST_USER)
                .build();

        Notification<Void> status = Observable.concat(
                requestOrange.observe(),
                requestBreakingBad.observe()).materialize().toBlocking().last();

        if (status.isOnError()) {
            System.err.println("ERROR: user recommendations update failure");
            status.getThrowable().printStackTrace();
            return false;
        }
        System.out.println("User recommendations updated.");
        return true;
    }

    private void searchCatalog() {
        System.out.println("Searching through the movie catalog:");

        RibbonRequest<ByteBuf> requestByUserId = recommendationsByUserIdTemplate.requestBuilder()
                .withRequestProperty("userId", TEST_USER)
                .build();
        RibbonRequest<ByteBuf> requestByCriteria = recommendationsByTemplate.requestBuilder()
                .withRequestProperty("category", "Drama")
                .withRequestProperty("ageGroup", "Adults")
                .build();

        List<String> searches = new ArrayList<String>(2);
        Collections.addAll(searches, "findById", "findRawMovieById", "findMovie(name, category)");

        Observable.concat(
                requestByUserId.observe(),
                requestByCriteria.observe()
        ).flatMap(new Func1<ByteBuf, Observable<List<Movie>>>() {
            @Override
            public Observable<List<Movie>> call(ByteBuf byteBuf) {
                List<Movie> movies = new ArrayList<Movie>();
                String lines = byteBuf.toString(Charset.defaultCharset());
                for (String line : NEW_LINE_SPLIT_RE.split(lines)) {
                    movies.add(Movie.from(line));
                }
                return Observable.just(movies);
            }
        }).zip(searches, new Func2<List<Movie>, String, Void>() {
            @Override
            public Void call(List<Movie> movies, String query) {
                System.out.println(format("%s=%s", query, movies));
                return null;
            }
        }).toBlocking().last();

    }

    private void shutdown() {
        HystrixTimer.reset();
    }

    public static void main(String[] args) {
        System.out.println("Starting templates based movie service...");
        try {
            RxMovieTemplateExample example = new RxMovieTemplateExample();
            example.registerMovies();
            example.updateRecommendations();
            example.searchCatalog();
            example.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
