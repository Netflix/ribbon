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

import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.ribbon.examples.rx.RxMovieServer;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.ProxyLifeCycle;
import io.netty.buffer.ByteBuf;
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
public class RxMovieProxyExample {

    private static final String TEST_USER = "user1";
    private static final Pattern NEW_LINE_SPLIT_RE = Pattern.compile("\n");

    private final MovieService movieService;

    public RxMovieProxyExample() {
        HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("movieServiceClient",
                ClientOptions.create()
                        .withMaxAutoRetriesNextServer(3)
                        .withConfigurationBasedServerList("localhost:" + RxMovieServer.DEFAULT_PORT));
        movieService = Ribbon.from(MovieService.class, httpResourceGroup);
    }

    private boolean registerMovies() {
        System.out.print("Registering movies...");
        Notification<Void> status = Observable.concat(
                movieService.registerMovie(Movie.ORANGE_IS_THE_NEW_BLACK).observe(),
                movieService.registerMovie(Movie.BREAKING_BAD).observe(),
                movieService.registerMovie(Movie.HOUSE_OF_CARDS).observe()).materialize().toBlocking().last();

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
        Notification<Void> status = Observable.concat(
                movieService.updateRecommendations(TEST_USER, Movie.ORANGE_IS_THE_NEW_BLACK.getId()).observe(),
                movieService.updateRecommendations(TEST_USER, Movie.BREAKING_BAD.getId()).observe()).materialize().toBlocking().last();

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
        List<String> searches = new ArrayList<String>(2);
        Collections.addAll(searches, "findById", "findRawMovieById", "findMovie(name, category)");
        Observable.concat(
                movieService.recommendationsByUserId(TEST_USER).observe(),
                movieService.recommendationsBy("Drama", "Adults").observe()
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

    public void shutdown() {
        System.out.println("Shutting down the proxy...");
        ((ProxyLifeCycle) movieService).shutdown();
        HystrixTimer.reset();
    }

    public static void main(String[] args) {
        System.out.println("Starting movie service proxy...");
        try {
            RxMovieProxyExample example = new RxMovieProxyExample();
            example.registerMovies();
            example.updateRecommendations();
            example.searchCatalog();
            example.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
