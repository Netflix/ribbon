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

import com.netflix.ribbon.examples.rx.common.Movie;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.ConcurrentSet;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.StringTransformer;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;

import java.nio.charset.Charset;
import java.util.Random;
import java.util.Set;

import static com.netflix.ribbon.examples.rx.common.Movie.*;
import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieServerTest {

    private static final String TEST_USER_ID = "user1";

    private static final Random RANDOM = new Random();

    private int port = RANDOM.nextInt(1000) + 8000;

    private String baseURL = "http://localhost:" + port;

    private RxMovieServer movieServer;

    private HttpServer<ByteBuf, ByteBuf> httpServer;

    @Before
    public void setUp() throws Exception {
        movieServer = new RxMovieServer(port);
        httpServer = movieServer.createServer().start();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.shutdown();
    }

    @Test
    public void testMovieRegistration() {
        String movieFormatted = ORANGE_IS_THE_NEW_BLACK.toString();

        HttpResponseStatus statusCode = RxNetty.createHttpPost(baseURL + "/movies", Observable.just(movieFormatted), new StringTransformer())
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<HttpResponseStatus>>() {
                    @Override
                    public Observable<HttpResponseStatus> call(HttpClientResponse<ByteBuf> httpClientResponse) {
                        return Observable.just(httpClientResponse.getStatus());
                    }
                }).toBlocking().first();

        assertEquals(HttpResponseStatus.CREATED, statusCode);
        assertEquals(ORANGE_IS_THE_NEW_BLACK, movieServer.movies.get(ORANGE_IS_THE_NEW_BLACK.getId()));
    }

    @Test
    public void testUpateRecommendations() {
        movieServer.movies.put(ORANGE_IS_THE_NEW_BLACK.getId(), ORANGE_IS_THE_NEW_BLACK);
        HttpResponseStatus statusCode = RxNetty.createHttpPost(baseURL + "/users/" + TEST_USER_ID + "/recommendations", Observable.just(ORANGE_IS_THE_NEW_BLACK.getId()), new StringTransformer())
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<HttpResponseStatus>>() {
                    @Override
                    public Observable<HttpResponseStatus> call(HttpClientResponse<ByteBuf> httpClientResponse) {
                        return Observable.just(httpClientResponse.getStatus());
                    }
                }).toBlocking().first();

        assertEquals(HttpResponseStatus.OK, statusCode);
        assertTrue(movieServer.userRecommendations.get(TEST_USER_ID).contains(ORANGE_IS_THE_NEW_BLACK.getId()));
    }

    @Test
    public void testRecommendationsByUserId() throws Exception {
        movieServer.movies.put(ORANGE_IS_THE_NEW_BLACK.getId(), ORANGE_IS_THE_NEW_BLACK);
        movieServer.movies.put(BREAKING_BAD.getId(), BREAKING_BAD);
        Set<String> userRecom = new ConcurrentSet<String>();
        userRecom.add(ORANGE_IS_THE_NEW_BLACK.getId());
        userRecom.add(BREAKING_BAD.getId());
        movieServer.userRecommendations.put(TEST_USER_ID, userRecom);

        Observable<HttpClientResponse<ByteBuf>> httpGet = RxNetty.createHttpGet(baseURL + "/users/" + TEST_USER_ID + "/recommendations");
        Movie[] movies = handleGetMoviesReply(httpGet);

        assertTrue(movies[0] != movies[1]);
        assertTrue(userRecom.contains(movies[0].getId()));
        assertTrue(userRecom.contains(movies[1].getId()));
    }

    @Test
    public void testRecommendationsByMultipleCriteria() throws Exception {
        movieServer.movies.put(ORANGE_IS_THE_NEW_BLACK.getId(), ORANGE_IS_THE_NEW_BLACK);
        movieServer.movies.put(BREAKING_BAD.getId(), BREAKING_BAD);
        movieServer.movies.put(HOUSE_OF_CARDS.getId(), HOUSE_OF_CARDS);

        String relativeURL = String.format("/recommendations?category=%s&ageGroup=%s", BREAKING_BAD.getCategory(), BREAKING_BAD.getAgeGroup());
        Movie[] movies = handleGetMoviesReply(RxNetty.createHttpGet(baseURL + relativeURL));

        assertEquals(1, movies.length);
        assertEquals(BREAKING_BAD, movies[0]);
    }

    private Movie[] handleGetMoviesReply(Observable<HttpClientResponse<ByteBuf>> httpGet) {
        return httpGet
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<Movie[]>>() {
                    @Override
                    public Observable<Movie[]> call(HttpClientResponse<ByteBuf> httpClientResponse) {
                        return httpClientResponse.getContent().map(new Func1<ByteBuf, Movie[]>() {
                            @Override
                            public Movie[] call(ByteBuf byteBuf) {
                                String[] lines = byteBuf.toString(Charset.defaultCharset()).split("\n");
                                Movie[] movies = new Movie[lines.length];
                                for (int i = 0; i < movies.length; i++) {
                                    movies[i] = Movie.from(lines[i]);
                                }
                                return movies;
                            }
                        });
                    }
                }).toBlocking().first();
    }

}
