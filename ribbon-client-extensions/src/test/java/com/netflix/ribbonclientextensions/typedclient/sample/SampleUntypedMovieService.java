package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Cache;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.HttpMethod;
import com.netflix.ribbonclientextensions.typedclient.annotation.Hystrix;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;
import io.netty.buffer.ByteBuf;

/**
 * @author Tomasz Bak
 */
@Hystrix(name = "movie")
public interface SampleUntypedMovieService {

    @Cache(key = "movie.{id}")
    @Http(method = HttpMethod.GET, path = "/movies/{id}")
    RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);

    @Cache(key = "movie#name={name},author={author}")
    @Http(method = HttpMethod.GET, path = "/movies?name={name}&author={author}")
    RibbonRequest<ByteBuf> findMovie(@Var("name") String name, @Var("author") String author);

    @Hystrix(name = "postMovie")
    @Http(method = HttpMethod.POST, path = "/movies")
    RibbonRequest<Void> registerMovie(@Content ByteBuf movie);
}
