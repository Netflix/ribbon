package com.netflix.ribbonclientextensions.evcache;

import static com.google.common.base.Preconditions.checkNotNull;

import com.netflix.evcache.EVCache;
import com.netflix.evcache.EVCacheException;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.ribbonclientextensions.hystrix.ClientCommand;
import com.netflix.ribbonclientextensions.hystrix.FallbackHandler;

public abstract class EvCacheCommand <T> extends ClientCommand<T> {

    private final EvCacheHandler<T> cacheHandler;
    protected final String key;

static  HystrixCommandGroupKey group = new HystrixCommandGroupKey() {
        @Override
        public String name() {
            return "evcache";
        }
    };

    protected EvCacheCommand(HystrixCommandKey command, EvCacheHandler cacheHandler, String key) {
        super(group, command, ExecutionIsolationStrategy.SEMAPHORE);
        this.cacheHandler = checkNotNull(cacheHandler, "'cache' cannot be null");
        this.key = key;
    }



    public EvCacheCommand(EvCacheHandler cacheHandler, String key) {
        this(HystrixCommandKey.Factory.asKey("EVCacheGetCommand"), cacheHandler, key);

    }

    @Override
    protected T run() throws CacheFaultException {
        try {
            return cacheHandler.get(key);
        } catch (EVCacheException e) {
           throw  new CacheFaultException(e);
        }
    }

    @Override
    protected String getCacheKey() {
        return cacheHandler.toString() + ":" + key;
    }

    @Override
    protected T getFallback() {
        return null;
    }
}