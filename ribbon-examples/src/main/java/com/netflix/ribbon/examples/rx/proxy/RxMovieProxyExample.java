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

import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.examples.rx.AbstractRxMovieClient;
import com.netflix.ribbon.examples.rx.RxMovieServer;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.ProxyLifeCycle;
import io.netty.buffer.ByteBuf;
import rx.Observable;

/**
 * Run {@link com.netflix.ribbon.examples.rx.RxMovieServer} prior to runnng this example!
 *
 * @author Tomasz Bak
 */
public class RxMovieProxyExample extends AbstractRxMovieClient {

    private final MovieService movieService;

    public RxMovieProxyExample() {
        HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("movieServiceClient",
                ClientOptions.create()
                        .withMaxAutoRetriesNextServer(3)
                        .withConfigurationBasedServerList("localhost:" + RxMovieServer.DEFAULT_PORT));
        movieService = Ribbon.from(MovieService.class, httpResourceGroup);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<Void>[] triggerMoviesRegistration() {
        return new Observable[]{
                movieService.registerMovie(Movie.ORANGE_IS_THE_NEW_BLACK).observe(),
                movieService.registerMovie(Movie.BREAKING_BAD).observe(),
                movieService.registerMovie(Movie.HOUSE_OF_CARDS).observe()
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<Void>[] triggerRecommendationsUpdate() {
        return new Observable[]{
                movieService.updateRecommendations(TEST_USER, Movie.ORANGE_IS_THE_NEW_BLACK.getId()).observe(),
                movieService.updateRecommendations(TEST_USER, Movie.BREAKING_BAD.getId()).observe()
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Observable<ByteBuf>[] triggerRecommendationsSearch() {
        return new Observable[]{
                movieService.recommendationsByUserId(TEST_USER).observe(),
                movieService.recommendationsBy("Drama", "Adults").observe()
        };
    }

    @Override
    public void shutdown() {
        super.shutdown();
        ((ProxyLifeCycle) movieService).shutdown();
    }

    public static void main(String[] args) {
        System.out.println("Starting proxy based movie service...");
        new RxMovieProxyExample().execute();
    }
}
