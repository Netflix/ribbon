package com.netflix.ribbonclientextensions;

import com.netflix.evcache.EVCacheException;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.ribbonclientextensions.evcache.EvCacheConfig;
import com.netflix.ribbonclientextensions.evcache.EvCacheHandler;
import com.netflix.ribbonclientextensions.hystrix.ClientCommand;

import java.net.URISyntaxException;

/**
 * Created by mcohen on 4/25/14.
 */
public class ResourceCommand<T> extends ClientCommand<T> {
    Resource<T> resource;

    protected ResourceCommand(HystrixCommandGroupKey group, HystrixCommandKey command, Resource<T> resource) {
        super(group, command);
    }

    @Override
    protected T run() throws Exception {
        if(resource.getResourceTemplate().primaryCacheConfig!= null){
            return getFromCache(resource.getResourceTemplate().primaryCacheConfig);
        }
        return null;//todo
    }

    private T getFromCache(CacheConfig cacheConfig) {
        if(cacheConfig instanceof EvCacheConfig){
            if(resource.getResourceTemplate().primaryCacheHandler == null){
                resource.getResourceTemplate().primaryCacheHandler = new EvCacheHandler<T>((EvCacheConfig) cacheConfig);
            }
            try {
                return ((EvCacheHandler<T>)(resource.getResourceTemplate().primaryCacheHandler)).get(resource.getCacheKey());
            } catch (EVCacheException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

        }
        return null;
    }

    protected T getFallback(){
        return null; //todo
    }
}
