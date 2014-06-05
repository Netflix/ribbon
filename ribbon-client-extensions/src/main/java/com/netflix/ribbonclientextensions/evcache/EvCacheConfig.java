package com.netflix.ribbonclientextensions.evcache;

import com.netflix.evcache.EVCache;
import com.netflix.evcache.EVCacheTranscoder;
import com.netflix.ribbonclientextensions.CacheConfig;

/**
 * Created by mcohen on 4/22/14.
 */
public class EvCacheConfig extends CacheConfig{

    EVCacheTranscoder transcoder;
    boolean touchOnGet = false;
    boolean enableZoneFallback = true;
    int timeToLive = EVCache.Builder.DEFAULT_TTL;
    String appName;
    String cacheName;

    public EvCacheConfig(String appName,
                         String cacheName,
                         boolean enableZoneFallback,
                         int timeToLive,
                         boolean touchOnGet,
                         EVCacheTranscoder transcoder,
                         String cacheKeyTemplate) {
        super(cacheKeyTemplate);
        this.appName = appName;
        this.cacheName = cacheName;
        this.enableZoneFallback = enableZoneFallback;
        this.timeToLive = timeToLive;
        this.touchOnGet = touchOnGet;
        this.transcoder = transcoder;
    }

    public EvCacheConfig(String appName,
                         String cacheName,
                         boolean enableZoneFallback,
                         int timeToLive,
                         boolean touchOnGet,
                         String cacheKeyTemplate) {
        super(cacheKeyTemplate);
        this.appName = appName;
        this.cacheName = cacheName;
        this.enableZoneFallback = enableZoneFallback;
        this.timeToLive = timeToLive;
        this.touchOnGet = touchOnGet;
    }

    public EvCacheConfig(String appName, String cacheName,String cacheKeyTemplate) {
        super(cacheKeyTemplate);
        this.appName = appName;
        this.cacheName = cacheName;
    }

    public EvCacheConfig(String appName, String cacheName, boolean enableZoneFallback,String cacheKeyTemplate) {
        super(cacheKeyTemplate);
        this.appName = appName;
        this.cacheName = cacheName;
        this.enableZoneFallback = enableZoneFallback;
    }

    public EvCacheConfig(String appName, String cacheName, boolean enableZoneFallback, int timeToLive, String cacheKeyTemplate) {
        super(cacheKeyTemplate);
        this.appName = appName;
        this.cacheName = cacheName;
        this.enableZoneFallback = enableZoneFallback;
        this.timeToLive = timeToLive;
    }
}
