package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.CacheProviders;
import com.netflix.ribbonclientextensions.typedclient.annotation.CacheProviders.Provider;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.ContentTransformerClass;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.Header;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.HttpMethod;
import com.netflix.ribbonclientextensions.typedclient.annotation.Hystrix;
import com.netflix.ribbonclientextensions.typedclient.annotation.ResourceGroupSpec;
import com.netflix.ribbonclientextensions.typedclient.annotation.TemplateName;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;
import com.netflix.ribbonclientextensions.typedclient.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbonclientextensions.typedclient.sample.HystrixHandlers.SampleHttpResponseValidator;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.RawContentSource;

import static com.netflix.ribbonclientextensions.typedclient.sample.ResourceGroupClasses.*;

/**
 * @author Tomasz Bak
 */
public class MovieServiceInterfaces {

    public static interface SampleMovieService {

        @TemplateName("findMovieById")
        @Http(
                method = HttpMethod.GET,
                path = "/movies/{id}",
                headers = {
                        @Header(name = "X-MyHeader1", value = "value1"),
                        @Header(name = "X-MyHeader2", value = "value2")
                })
        @Hystrix(
                cacheKey = "findMovieById/{id}",
                validator = SampleHttpResponseValidator.class,
                fallbackHandler = MovieFallbackHandler.class)
        @CacheProviders({@Provider(key = "findMovieById_{id}", provider = SampleCacheProviderFactory.class)})
        RibbonRequest<Movie> findMovieById(@Var("id") String id);

        @TemplateName("findRawMovieById")
        @Http(method = HttpMethod.GET, path = "/rawMovies/{id}")
        RibbonRequest<ByteBuf> findRawMovieById(@Var("id") String id);

        @TemplateName("findMovie")
        @Http(method = HttpMethod.GET, path = "/movies?name={name}&author={author}")
        RibbonRequest<Movie> findMovie(@Var("name") String name, @Var("author") String author);

        @TemplateName("registerMovie")
        @Http(method = HttpMethod.POST, path = "/movies")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> registerMovie(@Content Movie movie);

        @Http(method = HttpMethod.PUT, path = "/movies/{id}")
        RibbonRequest<Void> updateMovie(@Var("id") String id, @Content Movie movie);

        @Http(method = HttpMethod.PATCH, path = "/movies/{id}")
        RibbonRequest<Void> updateMoviePartial(@Var("id") String id, @Content Movie movie);

        @TemplateName("registerMovieRaw")
        @Http(method = HttpMethod.POST, path = "/movies")
        RibbonRequest<Void> registerMovieRaw(@Content RawContentSource<Movie> rawMovieContent);

        @TemplateName("registerTitle")
        @Http(method = HttpMethod.POST, path = "/titles")
        RibbonRequest<Void> registerTitle(@Content String title);

        @TemplateName("registerBinary")
        @Http(method = HttpMethod.POST, path = "/binaries")
        RibbonRequest<Void> registerBinary(@Content ByteBuf binary);

        @TemplateName("deleteMovie")
        @Http(method = HttpMethod.DELETE, path = "/movies/{id}")
        RibbonRequest<Void> deleteMovie(@Var("id") String id);
    }

    public static interface ShortMovieService {
        @TemplateName("findMovieById")
        @Http(method = HttpMethod.GET, path = "/movies/{id}")
        RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);

        @TemplateName("findMovieById")
        @Http(method = HttpMethod.GET, path = "/movies")
        RibbonRequest<ByteBuf> findAll();
    }

    @ResourceGroupSpec(name = "testResourceGroup")
    public static interface SampleMovieServiceWithResourceGroupNameAnnotation {
    }

    @ResourceGroupSpec(resourceGroupClass = SampleHttpResourceGroup.class)
    public static interface SampleMovieServiceWithResourceGroupClassAnnotation {
    }

    @ResourceGroupSpec(name = "testResourceGroup", resourceGroupClass = SampleHttpResourceGroup.class)
    public static interface BrokenMovieServiceWithResourceGroupNameAndClassAnnotation {
    }

    @ResourceGroupSpec(name = "testResourceGroup")
    public static interface HystrixOptionalAnnotationValues {
        @TemplateName("hystrix1")
        @Http(method = HttpMethod.GET, path = "/hystrix/1")
        @Hystrix(cacheKey = "findMovieById/{id}")
        RibbonRequest<Void> hystrixWithCacheKeyOnly();

        @TemplateName("hystrix2")
        @Http(method = HttpMethod.GET, path = "/hystrix/2")
        @Hystrix(validator = SampleHttpResponseValidator.class)
        RibbonRequest<Void> hystrixWithValidatorOnly();

        @TemplateName("hystrix3")
        @Http(method = HttpMethod.GET, path = "/hystrix/3")
        @Hystrix(fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Void> hystrixWithFallbackHandlerOnly();
    }

    public static interface BrokenMovieService {

        @Http(method = HttpMethod.GET)
        Movie returnTypeNotRibbonRequest();

        Movie missingHttpAnnotation();

        @Http(method = HttpMethod.GET)
        RibbonRequest<Void> multipleContentParameters(@Content Movie content1, @Content Movie content2);

    }
}
