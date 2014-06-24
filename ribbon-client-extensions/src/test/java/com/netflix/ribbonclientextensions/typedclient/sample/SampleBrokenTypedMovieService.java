package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http;
import com.netflix.ribbonclientextensions.typedclient.annotation.Http.HttpMethod;

/**
 * @author Tomasz Bak
 */
public interface SampleBrokenTypedMovieService {

    @Http(method = HttpMethod.GET)
    public Movie returnTypeNotRibbonRequest();

    public Movie missingHttpAnnotation();

    @Http(method = HttpMethod.GET)
    public RibbonRequest<Void> multipleContentParameters(@Content Movie content1, @Content Movie content2);
}
