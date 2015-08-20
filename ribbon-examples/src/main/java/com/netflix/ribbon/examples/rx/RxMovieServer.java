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

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.ribbon.examples.rx.common.Movie;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.ConcurrentSet;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.pipeline.PipelineConfigurators;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import rx.functions.Func1;

import static java.lang.String.*;

/**
 * The client examples assume that the movie server runs on the default port 8080.
 *
 * @author Tomasz Bak
 */
public class RxMovieServer {
    public static final int DEFAULT_PORT = 8080;

    private static final Pattern USER_RECOMMENDATIONS_PATH_RE = Pattern.compile(".*/users/([^/]*)/recommendations");

    private final int port;

    final Map<String, Movie> movies = new ConcurrentHashMap<String, Movie>();
    final Map<String, Set<String>> userRecommendations = new ConcurrentHashMap<String, Set<String>>();

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
        }).pipelineConfigurator(PipelineConfigurators.<ByteBuf, ByteBuf>httpServerConfigurator()).enableWireLogging(LogLevel.ERROR).build();

        System.out.println("RxMovie server started...");
        return server;
    }

    private Observable<Void> handleRecommendationsByUserId(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        System.out.println("HTTP request -> recommendations by user id request: " + request.getPath());
        final String userId = userIdFromPath(request.getPath());
        if (userId == null) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return response.close();
        }
        if (!userRecommendations.containsKey(userId)) {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return response.close();
        }

        StringBuilder builder = new StringBuilder();
        for (String movieId : userRecommendations.get(userId)) {
            System.out.println("    returning: " + movies.get(movieId));
            builder.append(movies.get(movieId)).append('\n');
        }

        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer();
        byteBuf.writeBytes(builder.toString().getBytes(Charset.defaultCharset()));

        response.write(byteBuf);
        return response.close();
    }

    private Observable<Void> handleRecommendationsBy(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        System.out.println(format("HTTP request -> recommendations by multiple criteria: %s?%s", request.getPath(), request.getQueryString()));
        List<String> category = request.getQueryParameters().get("category");
        List<String> ageGroup = request.getQueryParameters().get("ageGroup");
        if (category.isEmpty() || ageGroup.isEmpty()) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return response.close();
        }

        boolean any = false;
        StringBuilder builder = new StringBuilder();
        for (Movie movie : movies.values()) {
            if (movie.getCategory().equals(category.get(0)) && movie.getAgeGroup().equals(ageGroup.get(0))) {
                System.out.println("    returning: " + movie);
                builder.append(movie).append('\n');
                any = true;
            }
        }
        if (!any) {
            System.out.println("No movie matched the given criteria:");
            for (Movie movie : movies.values()) {
                System.out.print("    ");
                System.out.println(movie);
            }
        }

        ByteBuf byteBuf = UnpooledByteBufAllocator.DEFAULT.buffer();
        byteBuf.writeBytes(builder.toString().getBytes(Charset.defaultCharset()));

        response.write(byteBuf);
        return response.close();
    }

    private Observable<Void> handleUpdateRecommendationsForUser(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        System.out.println("HTTP request -> update recommendations for user: " + request.getPath());
        final String userId = userIdFromPath(request.getPath());
        if (userId == null) {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return response.close();
        }
        return request.getContent().flatMap(new Func1<ByteBuf, Observable<Void>>() {
            @Override
            public Observable<Void> call(ByteBuf byteBuf) {
                String movieId = byteBuf.toString(Charset.defaultCharset());
                System.out.println(format("    updating: {user=%s, movie=%s}", userId, movieId));
                synchronized (this) {
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
        System.out.println("Http request -> register movie: " + request.getPath());
        return request.getContent().flatMap(new Func1<ByteBuf, Observable<Void>>() {
            @Override
            public Observable<Void> call(ByteBuf byteBuf) {
                String formatted = byteBuf.toString(Charset.defaultCharset());
                System.out.println("    movie: " + formatted);
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

    private static String userIdFromPath(String path) {
        Matcher matcher = USER_RECOMMENDATIONS_PATH_RE.matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    public static void main(final String[] args) {
        new RxMovieServer(DEFAULT_PORT).createServer().startAndWait();
    }

}
