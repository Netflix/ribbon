package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Cache;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.HttpMethod;
import com.netflix.ribbonclientextensions.typedclient.annotation.Hystrix;
import com.netflix.ribbonclientextensions.typedclient.annotation.TemplateName;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;

/**
 * @author Tomasz Bak
 */
@Hystrix(name = "movie")
public interface SampleTypedMovieService {

    @TemplateName("findMovieById")
    @Cache(key = "movie.{id}")
    @Http(method = HttpMethod.GET, path = "/movies/{id}")
    RibbonRequest<Movie> findMovieById(@Var("id") String id);

    @TemplateName("findMovie")
    @Cache(key = "movie#name={name},author={author}")
    @Http(method = HttpMethod.GET, path = "/movies?name={name}&author={author}")
    RibbonRequest<Movie> findMovie(@Var("name") String name, @Var("author") String author);

    @TemplateName("registerMovie")
    @Hystrix(name = "postMovie")
    @Http(method = HttpMethod.POST, path = "/movies")
    RibbonRequest<Void> registerMovie(@Content Movie movie);
}
