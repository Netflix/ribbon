package com.netflix.ribbon.examples.rx.proxy;

import io.netty.buffer.ByteBuf;

import javax.inject.Inject;

import rx.Observable;

import com.google.inject.Singleton;
import com.netflix.ribbon.examples.rx.AbstractRxMovieClient;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.examples.rx.proxy.MovieService;
import com.netflix.ribbon.proxy.ProxyLifeCycle;

@Singleton
public class RxMovieProxyExample extends AbstractRxMovieClient {

    private final MovieService movieService;

    @Inject
    public RxMovieProxyExample(MovieService movieService) {
        this.movieService = movieService;
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
}
