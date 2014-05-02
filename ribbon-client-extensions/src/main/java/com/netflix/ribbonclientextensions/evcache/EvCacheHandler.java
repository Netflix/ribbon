package com.netflix.ribbonclientextensions.evcache;

import com.netflix.evcache.EVCache;
import com.netflix.evcache.EVCacheException;
import com.netflix.evcache.EVCacheTranscoder;
import com.netflix.ribbonclientextensions.CacheHandler;

/**
 * Created by mcohen on 4/22/14.
 */
public class EvCacheHandler<T> extends CacheHandler {

   EvCacheConfig config;
   EVCache  evCache;

    public EvCacheHandler(EvCacheConfig config) {
        this.config = config;
        EVCache.Builder builder = new EVCache.Builder();
        if(config.enableZoneFallback) builder.enableZoneFallback();
        builder.setDefaultTTL(config.timeToLive);
        builder.setAppName(config.appName);
        builder.setCacheName(config.cacheName);
        evCache = builder.build();
    }



   public T get(String key) throws EVCacheException {
       if(config.touchOnGet){

       }
       return evCache.get(key);
   }

    @Override
    public String toString(){
        return evCache.toString();
    }


}
