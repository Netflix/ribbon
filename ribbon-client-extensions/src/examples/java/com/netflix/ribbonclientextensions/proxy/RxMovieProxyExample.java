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

import com.netflix.ribbonclientextensions.Ribbon;
import rx.Observable;
import rx.functions.Func2;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.String.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieProxyExample {

    private final MovieService movieService;

    public RxMovieProxyExample() {
        movieService = Ribbon.from(MovieService.class);
    }

    private void registerMovies() throws URISyntaxException {
        System.out.println("Registering movies...");
        movieService.registerMovie(Movie.ORANGE_IS_THE_NEW_BLACK);
        movieService.registerMovie(Movie.BRAKING_BAD);
        movieService.registerMovie(Movie.HOUSE_OF_CARDS);
    }

    private void searchCatalog() {
        System.out.println("Searching through the movie catalog:");
        List<String> searches = new ArrayList<String>(2);
        Collections.addAll(searches, "findById", "findRawMovieById", "findMovie(name, category)");
        Observable.concat(
                movieService.recommendationsByUserId("1").observe(),
                movieService.recommendationsBy("Orange is the New Black", "Drama").observe()
        ).cast(Movie.class).zip(searches, new Func2<Movie, String, Void>() {
            @Override
            public Void call(Movie movie, String query) {
                System.out.println(format("%s=%s", query, movie));
                return null;
            }
        }).toBlocking().last();
    }

    public static void main(String[] args) {
        System.out.println("Starting movie service proxy...");
        try {
            RxMovieProxyExample example = new RxMovieProxyExample();
            example.registerMovies();
            example.searchCatalog();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
