package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Cache;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.ContentTransformerClass;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.HttpMethod;
import com.netflix.ribbonclientextensions.typedclient.annotation.Hystrix;
import com.netflix.ribbonclientextensions.typedclient.annotation.ResourceGroupSpec;
import com.netflix.ribbonclientextensions.typedclient.annotation.TemplateName;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;
import com.netflix.ribbonclientextensions.typedclient.sample.HystrixHandlers.GenericFallbackHandler;
import com.netflix.ribbonclientextensions.typedclient.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbonclientextensions.typedclient.sample.HystrixHandlers.SampleHttpResponseValidator;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import static com.netflix.ribbonclientextensions.typedclient.sample.ResourceGroupClasses.SampleHttpResourceGroup;

/**
 * @author Tomasz Bak
 */
public class MovieServiceInterfaces {

    @Hystrix(validator = SampleHttpResponseValidator.class, fallbackHandler = GenericFallbackHandler.class)
    public static interface SampleMovieService {

        @TemplateName("findMovieById")
        @Cache(key = "movie.{id}")
        @Http(method = HttpMethod.GET, path = "/movies/{id}")
        @Hystrix(validator = SampleHttpResponseValidator.class, fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Movie> findMovieById(@Var("id") String id);

        @TemplateName("findRawMovieById")
        @Cache(key = "movie.{id}")
        @Http(method = HttpMethod.GET, path = "/rawMovies/{id}")
        RibbonRequest<ByteBuf> findRawMovieById(@Var("id") String id);

        @TemplateName("findMovie")
        @Cache(key = "movie#name={name},author={author}")
        @Http(method = HttpMethod.GET, path = "/movies?name={name}&author={author}")
        @Hystrix(validator = SampleHttpResponseValidator.class, fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Movie> findMovie(@Var("name") String name, @Var("author") String author);

        @TemplateName("registerMovie")
        @Http(method = HttpMethod.POST, path = "/movies")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> registerMovie(@Content Movie movie);

        @TemplateName("registerMovieRaw")
        @Http(method = HttpMethod.POST, path = "/movies")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> registerMovieRaw(@Content RawContentSource<Movie> rawMovieContent);

        @TemplateName("registerTitle")
        @Http(method = HttpMethod.POST, path = "/titles")
        RibbonRequest<Void> registerTitle(@Content String title);

        @TemplateName("registerBinary")
        @Http(method = HttpMethod.POST, path = "/binaries")
        RibbonRequest<Void> registerBinary(@Content ByteBuf binary);
    }

    @ResourceGroupSpec(name="testResourceGroup")
    public static interface SampleMovieServiceWithResourceGroupNameAnnotation {
    }

    @ResourceGroupSpec(resourceGroupClass=SampleHttpResourceGroup.class)
    public static interface SampleMovieServiceWithResourceGroupClassAnnotation {
    }

    @ResourceGroupSpec(name="testResourceGroup", resourceGroupClass=SampleHttpResourceGroup.class)
    public static interface BrokenMovieServiceWithResourceGroupNameAndClassAnnotation {
    }

    public static interface BrokenMovieService {

        @Http(method = HttpMethod.GET)
        Movie returnTypeNotRibbonRequest();

        Movie missingHttpAnnotation();
        @Http(method = HttpMethod.GET)
        RibbonRequest<Void> multipleContentParameters(@Content Movie content1, @Content Movie content2);

    }
}
