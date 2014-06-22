package com.netflix.ribbonclientextensions;

import io.reactivex.netty.protocol.http.client.HttpClientResponse;


/**
 * 
 * @author awang
 *
 * @param <T> Protocol specific response meta data, e.g., HttpClientResponse
 */
public interface ResponseValidator<T> {
    /**
     * @param response Protocol specific response object, e.g., {@link HttpClientResponse}
     * @throws UnsuccessfulResponseException throw if server is able to execute the request, but 
     *              returns an an unsuccessful response.
     *              For example, HTTP response with 404 status code. This will be treated as a valid
     *              response and will not trigger Hystrix fallback
     * @throws ServerError throw if the response indicates that there is an server error in executing the request. 
     *              For example, HTTP response with 500 status code. This will trigger Hystrix fallback.
     */
    public void validate(T response) throws UnsuccessfulResponseException, ServerError;
}
