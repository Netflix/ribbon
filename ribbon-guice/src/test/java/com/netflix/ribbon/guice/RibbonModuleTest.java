package com.netflix.ribbon.guice;


import io.netty.buffer.ByteBuf;
import io.reactivex.netty.channel.StringTransformer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.junit.Test;

import rx.Observable;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.ribbon.ClientConfigFactory;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.RibbonTransportFactory;
import com.netflix.ribbon.examples.rx.AbstractRxMovieClient;
import com.netflix.ribbon.examples.rx.common.Movie;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceFallbackHandler;
import com.netflix.ribbon.examples.rx.common.RecommendationServiceResponseValidator;
import com.netflix.ribbon.examples.rx.common.RxMovieTransformer;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;


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

            registerMovieTemplate = httpResourceGroup.newRequestTemplate("registerMovie", ByteBuf.class)
                    .withMethod("POST")
                    .withUriTemplate("/movies")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withResponseValidator(new RecommendationServiceResponseValidator());

            updateRecommendationTemplate = httpResourceGroup.newRequestTemplate("updateRecommendation", ByteBuf.class)
                    .withMethod("POST")
                    .withUriTemplate("/users/{userId}/recommendations")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withResponseValidator(new RecommendationServiceResponseValidator());

            recommendationsByUserIdTemplate = httpResourceGroup.newRequestTemplate("recommendationsByUserId", ByteBuf.class)
                    .withMethod("GET")
                    .withUriTemplate("/users/{userId}/recommendations")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withFallbackProvider(new RecommendationServiceFallbackHandler())
                    .withResponseValidator(new RecommendationServiceResponseValidator());

            recommendationsByTemplate = httpResourceGroup.newRequestTemplate("recommendationsBy", ByteBuf.class)
                    .withMethod("GET")
                    .withUriTemplate("/recommendations?category={category}&ageGroup={ageGroup}")
                    .withHeader("X-Platform-Version", "xyz")
                    .withHeader("X-Auth-Token", "abc")
                    .withFallbackProvider(new RecommendationServiceFallbackHandler())
                    .withResponseValidator(new RecommendationServiceResponseValidator());
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
