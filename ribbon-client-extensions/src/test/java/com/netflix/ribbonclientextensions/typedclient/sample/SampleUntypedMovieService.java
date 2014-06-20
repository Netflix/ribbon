package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Cache;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.GET;
import com.netflix.ribbonclientextensions.typedclient.annotation.Hystrix;
import com.netflix.ribbonclientextensions.typedclient.annotation.POST;
import com.netflix.ribbonclientextensions.typedclient.annotation.Path;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;
import io.netty.buffer.ByteBuf;

/**
 * @author Tomasz Bak
 */
@Hystrix(name = "movie")
public interface SampleUntypedMovieService {

    @Cache(key = "movie.{id}")
    @GET
    @Path("/movies/{id}")
    RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);

    @Cache(key = "movie#name={name},author={author}")
    @GET
    @Path("/movies?name={name}&author={author}")
    RibbonRequest<ByteBuf> findMovie(@Var("name") String name, @Var("author") String author);

    @Hystrix(name = "postMovie")
    @POST
    @Path("/movies")
    RibbonRequest<Void> registerMovie(@Content ByteBuf movie);
}
