package com.netflix.ribbon.proxy.sample;

import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.CacheProviders;
import com.netflix.ribbon.proxy.annotation.Content;
import com.netflix.ribbon.proxy.annotation.ContentTransformerClass;
import com.netflix.ribbon.proxy.annotation.EvCache;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.ResourceGroup;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import com.netflix.ribbon.proxy.annotation.CacheProviders.Provider;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.sample.EvCacheClasses.SampleEVCacheTranscoder;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.SampleHttpResponseValidator;

import io.netty.buffer.ByteBuf;

import static com.netflix.ribbon.proxy.sample.ResourceGroupClasses.*;

/**
 * @author Tomasz Bak
 */
public class MovieServiceInterfaces {

    public static interface SampleMovieService {

        @TemplateName("findMovieById")
        @Http(
                method = HttpMethod.GET,
                uriTemplate = "/movies/{id}",
                headers = {
                        @Header(name = "X-MyHeader1", value = "value1.1"),
                        @Header(name = "X-MyHeader1", value = "value1.2"),
                        @Header(name = "X-MyHeader2", value = "value2")
                })
        @Hystrix(
                cacheKey = "findMovieById/{id}",
                validator = SampleHttpResponseValidator.class,
                fallbackHandler = MovieFallbackHandler.class)
        @CacheProviders(@Provider(key = "findMovieById_{id}", provider = SampleCacheProviderFactory.class))
        @EvCache(name = "movie-cache", appName = "movieService", cacheKeyTemplate = "movie-{id}", ttl = 50,
                enableZoneFallback = true, transcoder = SampleEVCacheTranscoder.class)
        RibbonRequest<Movie> findMovieById(@Var("id") String id);

        @TemplateName("findRawMovieById")
        @Http(method = HttpMethod.GET, uriTemplate = "/rawMovies/{id}")
        RibbonRequest<ByteBuf> findRawMovieById(@Var("id") String id);

        @TemplateName("findMovie")
        @Http(method = HttpMethod.GET, uriTemplate = "/movies?name={name}&author={author}")
        RibbonRequest<Movie> findMovie(@Var("name") String name, @Var("author") String author);

        @TemplateName("registerMovie")
        @Http(method = HttpMethod.POST, uriTemplate = "/movies")
        @Hystrix(cacheKey = "registerMovie", fallbackHandler = MovieFallbackHandler.class)
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> registerMovie(@Content Movie movie);

        @Http(method = HttpMethod.PUT, uriTemplate = "/movies/{id}")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> updateMovie(@Var("id") String id, @Content Movie movie);

        @Http(method = HttpMethod.PATCH, uriTemplate = "/movies/{id}")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> updateMoviePartial(@Var("id") String id, @Content Movie movie);

        @TemplateName("registerTitle")
        @Http(method = HttpMethod.POST, uriTemplate = "/titles")
        @Hystrix(cacheKey = "registerTitle", fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Void> registerTitle(@Content String title);

        @TemplateName("registerByteBufBinary")
        @Http(method = HttpMethod.POST, uriTemplate = "/binaries/byteBuf")
        @Hystrix(cacheKey = "registerByteBufBinary", fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Void> registerByteBufBinary(@Content ByteBuf binary);

        @TemplateName("registerByteArrayBinary")
        @Http(method = HttpMethod.POST, uriTemplate = "/binaries/byteArray")
        @Hystrix(cacheKey = "registerByteArrayBinary", fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Void> registerByteArrayBinary(@Content byte[] binary);

        @TemplateName("deleteMovie")
        @Http(method = HttpMethod.DELETE, uriTemplate = "/movies/{id}")
        RibbonRequest<Void> deleteMovie(@Var("id") String id);
    }

    public static interface ShortMovieService {
        @TemplateName("findMovieById")
        @Http(method = HttpMethod.GET, uriTemplate = "/movies/{id}")
        RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);

        @TemplateName("findMovieById")
        @Http(method = HttpMethod.GET, uriTemplate = "/movies")
        RibbonRequest<ByteBuf> findAll();
    }

    public static interface BrokenMovieService {

        @Http(method = HttpMethod.GET)
        Movie returnTypeNotRibbonRequest();

        Movie missingHttpAnnotation();

        @Http(method = HttpMethod.GET)
        RibbonRequest<Void> multipleContentParameters(@Content Movie content1, @Content Movie content2);
    }

    @ResourceGroup(name = "testResourceGroup")
    public static interface SampleMovieServiceWithResourceGroupNameAnnotation {

    }

    @ResourceGroup(resourceGroupClass = SampleHttpResourceGroup.class)
    public static interface SampleMovieServiceWithResourceGroupClassAnnotation {

    }

    @ResourceGroup(name = "testResourceGroup", resourceGroupClass = SampleHttpResourceGroup.class)
    public static interface BrokenMovieServiceWithResourceGroupNameAndClassAnnotation {

    }

    @ResourceGroup(name = "testResourceGroup")
    public static interface TemplateNameDerivedFromMethodName {
        @Http(method = HttpMethod.GET, uriTemplate = "/template")
        RibbonRequest<Void> myTemplateName();
    }

    @ResourceGroup(name = "testResourceGroup")
    public static interface HystrixOptionalAnnotationValues {

        @TemplateName("hystrix1")
        @Http(method = HttpMethod.GET, uriTemplate = "/hystrix/1")
        @Hystrix(cacheKey = "findMovieById/{id}")
        RibbonRequest<Void> hystrixWithCacheKeyOnly();

        @TemplateName("hystrix2")
        @Http(method = HttpMethod.GET, uriTemplate = "/hystrix/2")
        @Hystrix(validator = SampleHttpResponseValidator.class)
        RibbonRequest<Void> hystrixWithValidatorOnly();

        @TemplateName("hystrix3")
        @Http(method = HttpMethod.GET, uriTemplate = "/hystrix/3")
        @Hystrix(fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<Void> hystrixWithFallbackHandlerOnly();

    }

    @ResourceGroup(name = "testResourceGroup")
    public static interface PostsWithDifferentContentTypes {

        @TemplateName("rawContentSource")
        @Http(method = HttpMethod.POST, uriTemplate = "/content/rawContentSource")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> postwithRawContentSource(AtomicReference<Object> arg1, int arg2, @Content Observable<Movie> movie);
        
        @TemplateName("byteBufContent")
        @Http(method = HttpMethod.POST, uriTemplate = "/content/byteBufContent")
        RibbonRequest<Void> postwithByteBufContent(@Content ByteBuf byteBuf);

        @TemplateName("byteArrayContent")
        @Http(method = HttpMethod.POST, uriTemplate = "/content/byteArrayContent")
        RibbonRequest<Void> postwithByteArrayContent(@Content byte[] bytes);

        @TemplateName("stringContent")
        @Http(method = HttpMethod.POST, uriTemplate = "/content/stringContent")
        RibbonRequest<Void> postwithStringContent(@Content String content);

        @TemplateName("movieContent")
        @Http(method = HttpMethod.POST, uriTemplate = "/content/movieContent")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<Void> postwithMovieContent(@Content Movie movie);

        @TemplateName("movieContentBroken")
        @Http(method = HttpMethod.POST, uriTemplate = "/content/movieContentBroken")
        RibbonRequest<Void> postwithMovieContentBroken(@Content Movie movie);

    }
}
