package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Cache;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.HttpMethod;
import com.netflix.ribbonclientextensions.typedclient.annotation.Hystrix;
import com.netflix.ribbonclientextensions.typedclient.annotation.ResourceGroupSpec;
import com.netflix.ribbonclientextensions.typedclient.annotation.TemplateName;
import com.netflix.ribbonclientextensions.typedclient.annotation.Var;

import static com.netflix.ribbonclientextensions.typedclient.sample.ResourceGroupClasses.SampleHttpResourceGroup;

/**
 * @author Tomasz Bak
 */
public class MovieServiceInterfaces {

    @Hystrix(validator = Object.class, fallbackHandler = Object.class)
    public static interface SampleMovieService {

        @TemplateName("findMovieById")
        @Cache(key = "movie.{id}")
        @Http(method = HttpMethod.GET, path = "/movies/{id}")
        RibbonRequest<Movie> findMovieById(@Var("id") String id);

        @TemplateName("findMovie")
        @Cache(key = "movie#name={name},author={author}")
        @Http(method = HttpMethod.GET, path = "/movies?name={name}&author={author}")
        RibbonRequest<Movie> findMovie(@Var("name") String name, @Var("author") String author);

        @TemplateName("registerMovie")
        @Hystrix(validator = Object.class, fallbackHandler = Object.class)
        @Http(method = HttpMethod.POST, path = "/movies")
        RibbonRequest<Void> registerMovie(@Content Movie movie);
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

    @ResourceGroupSpec(resourceGroupClass=Object.class)
    public static interface BrokenMovieServiceWithInvalidResourceGroupClassAnnotation {
    }

    public static interface BrokenMovieService {

        @Http(method = HttpMethod.GET)
        Movie returnTypeNotRibbonRequest();

        Movie missingHttpAnnotation();
        @Http(method = HttpMethod.GET)
        RibbonRequest<Void> multipleContentParameters(@Content Movie content1, @Content Movie content2);

    }
}
