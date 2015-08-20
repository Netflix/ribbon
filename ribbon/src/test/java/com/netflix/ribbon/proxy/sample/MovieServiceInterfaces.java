package com.netflix.ribbon.proxy.sample;

import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.proxy.annotation.CacheProvider;
import com.netflix.ribbon.proxy.annotation.ClientProperties;
import com.netflix.ribbon.proxy.annotation.ClientProperties.Property;
import com.netflix.ribbon.proxy.annotation.Content;
import com.netflix.ribbon.proxy.annotation.ContentTransformerClass;
import com.netflix.ribbon.proxy.annotation.Http;
import com.netflix.ribbon.proxy.annotation.Http.Header;
import com.netflix.ribbon.proxy.annotation.Http.HttpMethod;
import com.netflix.ribbon.proxy.annotation.Hystrix;
import com.netflix.ribbon.proxy.annotation.ResourceGroup;
import com.netflix.ribbon.proxy.annotation.TemplateName;
import com.netflix.ribbon.proxy.annotation.Var;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.MovieFallbackHandler;
import com.netflix.ribbon.proxy.sample.HystrixHandlers.SampleHttpResponseValidator;
import io.netty.buffer.ByteBuf;
import rx.Observable;

import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.ribbon.proxy.sample.ResourceGroupClasses.SampleHttpResourceGroup;

/**
 * @author Tomasz Bak
 */
public class MovieServiceInterfaces {

    @ClientProperties(properties = {
            @Property(name="ReadTimeout", value="2000"),
            @Property(name="ConnectTimeout", value="1000"),
            @Property(name="MaxAutoRetriesNextServer", value="2")
    }, exportToArchaius = true)
    public static interface SampleMovieService {

        @TemplateName("findMovieById")
        @Http(
                method = HttpMethod.GET,
                uri = "/movies/{id}",
                headers = {
                        @Header(name = "X-MyHeader1", value = "value1.1"),
                        @Header(name = "X-MyHeader1", value = "value1.2"),
                        @Header(name = "X-MyHeader2", value = "value2")
                })
        @Hystrix(
                cacheKey = "findMovieById/{id}",
                validator = SampleHttpResponseValidator.class,
                fallbackHandler = MovieFallbackHandler.class)
        @CacheProvider(key = "findMovieById_{id}", provider = SampleCacheProviderFactory.class)
        RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);

        @TemplateName("findRawMovieById")
        @Http(method = HttpMethod.GET, uri = "/rawMovies/{id}")
        RibbonRequest<ByteBuf> findRawMovieById(@Var("id") String id);

        @TemplateName("findMovie")
        @Http(method = HttpMethod.GET, uri = "/movies?name={name}&author={author}")
        RibbonRequest<ByteBuf> findMovie(@Var("name") String name, @Var("author") String author);

        @TemplateName("registerMovie")
        @Http(method = HttpMethod.POST, uri = "/movies")
        @Hystrix(cacheKey = "registerMovie", fallbackHandler = MovieFallbackHandler.class)
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<ByteBuf> registerMovie(@Content Movie movie);

        @Http(method = HttpMethod.PUT, uri = "/movies/{id}")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<ByteBuf> updateMovie(@Var("id") String id, @Content Movie movie);

        @Http(method = HttpMethod.PATCH, uri = "/movies/{id}")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<ByteBuf> updateMoviePartial(@Var("id") String id, @Content Movie movie);

        @TemplateName("registerTitle")
        @Http(method = HttpMethod.POST, uri = "/titles")
        @Hystrix(cacheKey = "registerTitle", fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<ByteBuf> registerTitle(@Content String title);

        @TemplateName("registerByteBufBinary")
        @Http(method = HttpMethod.POST, uri = "/binaries/byteBuf")
        @Hystrix(cacheKey = "registerByteBufBinary", fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<ByteBuf> registerByteBufBinary(@Content ByteBuf binary);

        @TemplateName("registerByteArrayBinary")
        @Http(method = HttpMethod.POST, uri = "/binaries/byteArray")
        @Hystrix(cacheKey = "registerByteArrayBinary", fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<ByteBuf> registerByteArrayBinary(@Content byte[] binary);

        @TemplateName("deleteMovie")
        @Http(method = HttpMethod.DELETE, uri = "/movies/{id}")
        RibbonRequest<ByteBuf> deleteMovie(@Var("id") String id);
    }

    public static interface ShortMovieService {
        @TemplateName("findMovieById")
        @Http(method = HttpMethod.GET, uri = "/movies/{id}")
        RibbonRequest<ByteBuf> findMovieById(@Var("id") String id);

        @TemplateName("findMovieById")
        @Http(method = HttpMethod.GET, uri = "/movies")
        RibbonRequest<ByteBuf> findAll();
    }

    public static interface BrokenMovieService {

        @Http(method = HttpMethod.GET)
        Movie returnTypeNotRibbonRequest();

        Movie missingHttpAnnotation();

        @Http(method = HttpMethod.GET)
        RibbonRequest<ByteBuf> multipleContentParameters(@Content Movie content1, @Content Movie content2);
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
        @Http(method = HttpMethod.GET, uri = "/template")
        RibbonRequest<ByteBuf> myTemplateName();
    }

    @ResourceGroup(name = "testResourceGroup")
    public static interface HystrixOptionalAnnotationValues {

        @TemplateName("hystrix1")
        @Http(method = HttpMethod.GET, uri = "/hystrix/1")
        @Hystrix(cacheKey = "findMovieById/{id}")
        RibbonRequest<ByteBuf> hystrixWithCacheKeyOnly();

        @TemplateName("hystrix2")
        @Http(method = HttpMethod.GET, uri = "/hystrix/2")
        @Hystrix(validator = SampleHttpResponseValidator.class)
        RibbonRequest<ByteBuf> hystrixWithValidatorOnly();

        @TemplateName("hystrix3")
        @Http(method = HttpMethod.GET, uri = "/hystrix/3")
        @Hystrix(fallbackHandler = MovieFallbackHandler.class)
        RibbonRequest<ByteBuf> hystrixWithFallbackHandlerOnly();

    }

    @ResourceGroup(name = "testResourceGroup")
    public static interface PostsWithDifferentContentTypes {

        @TemplateName("rawContentSource")
        @Http(method = HttpMethod.POST, uri = "/content/rawContentSource")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<ByteBuf> postwithRawContentSource(AtomicReference<Object> arg1, int arg2, @Content Observable<Movie> movie);

        @TemplateName("byteBufContent")
        @Http(method = HttpMethod.POST, uri = "/content/byteBufContent")
        RibbonRequest<ByteBuf> postwithByteBufContent(@Content ByteBuf byteBuf);

        @TemplateName("byteArrayContent")
        @Http(method = HttpMethod.POST, uri = "/content/byteArrayContent")
        RibbonRequest<ByteBuf> postwithByteArrayContent(@Content byte[] bytes);

        @TemplateName("stringContent")
        @Http(method = HttpMethod.POST, uri = "/content/stringContent")
        RibbonRequest<ByteBuf> postwithStringContent(@Content String content);

        @TemplateName("movieContent")
        @Http(method = HttpMethod.POST, uri = "/content/movieContent")
        @ContentTransformerClass(MovieTransformer.class)
        RibbonRequest<ByteBuf> postwithMovieContent(@Content Movie movie);

        @TemplateName("movieContentBroken")
        @Http(method = HttpMethod.POST, uri = "/content/movieContentBroken")
        RibbonRequest<ByteBuf> postwithMovieContentBroken(@Content Movie movie);

    }
}
