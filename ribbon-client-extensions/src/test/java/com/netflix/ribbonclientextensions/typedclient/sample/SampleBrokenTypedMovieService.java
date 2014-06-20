package com.netflix.ribbonclientextensions.typedclient.sample;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.typedclient.annotation.Content;
import com.netflix.ribbonclientextensions.typedclient.annotation.GET;

/**
 * @author Tomasz Bak
 */
public interface SampleBrokenTypedMovieService {

    @GET
    public Movie returnTypeNotRibbonRequest();

    public Movie missingHttpMethod();

    @GET
    public RibbonRequest<Void> multipleContentParameters(@Content Movie content1, @Content Movie content2);
}
