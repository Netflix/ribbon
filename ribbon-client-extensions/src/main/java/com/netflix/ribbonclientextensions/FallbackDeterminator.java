package com.netflix.ribbonclientextensions;

/**
 * 
 * @author awang
 *
 * @param <T> Protocol specific response meta data, e.g., HttpClientResponse
 */
public interface FallbackDeterminator<T> {

    public boolean shouldTriggerFallback(T responseMetaData);
}
