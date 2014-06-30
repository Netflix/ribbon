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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.internal.ConcurrentSet;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import rx.functions.Func1;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.*;

/**
 * @author Tomasz Bak
 */
public class RxMovieServer {
    public static final int DEFAULT_PORT = 8080;

    private static final Pattern USER_RECOMMENDATIONS_PATH_RE = Pattern.compile(".*/users/([^/]*)/recommendations");

    private final int port;

    // FIXME Make it package private once this and the test class are in the same package.
    public final Map<String, Movie> movies = new ConcurrentHashMap<String, Movie>();
    public final Map<String, Set<String>> userRecommendations = new ConcurrentHashMap<String, Set<String>>();

    public RxMovieServer(int port) {
        this.port = port;
    }

    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.newHttpServerBuilder(port, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
                if (request.getPath().contains("/users")) {
                    if (request.getHttpMethod().equals(HttpMethod.GET)) {
                        return handleRecommendationsByUserId(request, response);
                    } else {
                        return handleUpdateRecommendationsForUser(request, response);
                    }
                }
                if (request.getPath().contains("/recommendations")) {
                    return handleRecommendationsBy(request, response);
                }
                if (request.getPath().contains("/movies")) {
                    return handleRegisterMovie(request, response);
                }
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                return response.close();
            }
        }).pipelineConfigurator(PipelineConfigurators.<ByteBuf, ByteBuf>httpServerConfigurator()).build();

        System.out.println("RxMovie server started...");
        return server;
    }

    private Observable<Void> handleRecommendationsByUserId(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        final String userId = userIdFromPath(request.getPath());
        if (userId == null) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return Observable.error(new IllegalArgumentException("Invalid URL"));
        }
        if (!userRecommendations.containsKey(userId)) {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.error(new IllegalArgumentException("No recommendations for the user " + userId));
        }

        StringBuilder builder = new StringBuilder();
        for (String movieId : userRecommendations.get(userId)) {
            builder.append(movies.get(movieId)).append('\n');
        }

        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer();
        byteBuf.writeBytes(builder.toString().getBytes(Charset.defaultCharset()));

        response.write(byteBuf);
        return response.close();
    }

    private Observable<Void> handleRecommendationsBy(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        List<String> category = request.getQueryParameters().get("category");
        List<String> ageGroup = request.getQueryParameters().get("ageGroup");
        if (category.isEmpty() || ageGroup.isEmpty()) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return Observable.error(new IllegalArgumentException("Invalid URL"));
        }

        StringBuilder builder = new StringBuilder();
        for (Movie movie : movies.values()) {
            if (movie.getCategory().equals(category.get(0)) && movie.getAgeGroup().equals(ageGroup.get(0))) {
                builder.append(movie).append('\n');
            }
        }

        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer();
        byteBuf.writeBytes(builder.toString().getBytes(Charset.defaultCharset()));

        response.write(byteBuf);
        return response.close();
    }

    private Observable<Void> handleUpdateRecommendationsForUser(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        final String userId = userIdFromPath(request.getPath());
        if (userId == null) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return Observable.error(new IllegalArgumentException("Invalid URL"));
        }
        return request.getContent().flatMap(new Func1<ByteBuf, Observable<Void>>() {
            @Override
            public Observable<Void> call(ByteBuf byteBuf) {
                String movieId = byteBuf.toString(Charset.defaultCharset());
                System.out.println(format("Updating recommendation for user %s and movie %s ", userId, movieId));
                synchronized (userRecommendations) {
                    Set<String> recommendations;
                    if (userRecommendations.containsKey(userId)) {
                        recommendations = userRecommendations.get(userId);
                    } else {
                        recommendations = new ConcurrentSet<String>();
                        userRecommendations.put(userId, recommendations);
                    }
                    recommendations.add(movieId);
                }
                response.setStatus(HttpResponseStatus.OK);
                return response.close();
            }
        });
    }

    private Observable<Void> handleRegisterMovie(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        return request.getContent().flatMap(new Func1<ByteBuf, Observable<Void>>() {
            @Override
            public Observable<Void> call(ByteBuf byteBuf) {
                String formatted = byteBuf.toString(Charset.defaultCharset());
                System.out.println("Registering movie " + formatted);
                try {
                    Movie movie = Movie.from(formatted);
                    movies.put(movie.getId(), movie);
                    response.setStatus(HttpResponseStatus.CREATED);
                } catch (Exception e) {
                    System.err.println("Invalid movie content");
                    e.printStackTrace();
                    response.setStatus(HttpResponseStatus.BAD_REQUEST);
                }
                return response.close();
            }
        });
    }

    private String userIdFromPath(String path) {
        Matcher matcher = USER_RECOMMENDATIONS_PATH_RE.matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    public static void main(final String[] args) {
        new RxMovieServer(DEFAULT_PORT).createServer().startAndWait();
    }

}
