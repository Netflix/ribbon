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

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.config.ConfigurationManager;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.examples.rx.AbstractRxMovieClient;
import com.netflix.ribbon.examples.rx.RxMovieServer;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.proxy.ProxyLifeCycle;
import io.netty.buffer.ByteBuf;
import rx.Observable;

/**
 * Run {@link com.netflix.ribbon.examples.rx.RxMovieServer} prior to running this example!
 *
 * @author Tomasz Bak
 */
public class RxMovieProxyExample extends AbstractRxMovieClient {

    private final MovieService movieService;

    public RxMovieProxyExample(int port) {
        ConfigurationManager.getConfigInstance().setProperty("MovieService.ribbon." + CommonClientConfigKey.MaxAutoRetriesNextServer, "3");
        ConfigurationManager.getConfigInstance().setProperty("MovieService.ribbon." + CommonClientConfigKey.ListOfServers, "localhost:" + port);
        movieService = Ribbon.from(MovieService.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<ByteBuf>[] triggerMoviesRegistration() {
        return new Observable[]{
                movieService.registerMovie(Movie.ORANGE_IS_THE_NEW_BLACK).toObservable(),
                movieService.registerMovie(Movie.BREAKING_BAD).toObservable(),
                movieService.registerMovie(Movie.HOUSE_OF_CARDS).toObservable()
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<ByteBuf>[] triggerRecommendationsUpdate() {
        return new Observable[]{
                movieService.updateRecommendations(TEST_USER, Movie.ORANGE_IS_THE_NEW_BLACK.getId()).toObservable(),
                movieService.updateRecommendations(TEST_USER, Movie.BREAKING_BAD.getId()).toObservable()
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<ByteBuf>[] triggerRecommendationsSearch() {
        return new Observable[]{
                movieService.recommendationsByUserId(TEST_USER).toObservable(),
                movieService.recommendationsBy("Drama", "Adults").toObservable()
        };
    }

    @Override
    public void shutdown() {
        super.shutdown();
        ((ProxyLifeCycle) movieService).shutdown();
    }

    public static void main(String[] args) {
        System.out.println("Starting proxy based movie service...");
        new RxMovieProxyExample(RxMovieServer.DEFAULT_PORT).runExample();
    }
}
