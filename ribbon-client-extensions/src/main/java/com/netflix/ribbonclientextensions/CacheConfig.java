package com.netflix.ribbonclientextensions;

/**
 * Created by mcohen on 4/23/14.
 */
abstract public class CacheConfig {
    protected final String cacheKeyTemplate;

    public CacheConfig(String cacheKeyTemplate){
        this.cacheKeyTemplate = cacheKeyTemplate;
    }
}
