package com.netflix.ribbon.proxy.processor;

import com.netflix.evcache.EVCacheTranscoder;
import com.netflix.ribbon.ResourceGroup.GroupBuilder;
import com.netflix.ribbon.ResourceGroup.TemplateBuilder;
import com.netflix.ribbon.RibbonResourceFactory;
import com.netflix.ribbon.evache.EvCacheOptions;
import com.netflix.ribbon.evache.EvCacheProvider;
import com.netflix.ribbon.proxy.ProxyAnnotationException;
import com.netflix.ribbon.proxy.Utils;
import com.netflix.ribbon.proxy.annotation.EvCache;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Allen Wang
 */
public class EVCacheAnnotationProcessor implements AnnotationProcessor<GroupBuilder, TemplateBuilder> {

    private static final class CacheId {
        private final String appName;
        private final String cacheName;

        CacheId(String appName, String cacheName) {
            this.appName = appName;
            this.cacheName = cacheName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheId cacheId = (CacheId) o;

            if (!appName.equals(cacheId.appName)) {
                return false;
            }
            return cacheName.equals(cacheId.cacheName);
        }

        @Override
        public int hashCode() {
            int result = appName.hashCode();
            result = 31 * result + cacheName.hashCode();
            return result;
        }
    }

    private Map<CacheId, EvCacheProvider<?>> evCacheProviderPool = new HashMap<CacheId, EvCacheProvider<?>>();

    @Override
    public void process(String templateName, TemplateBuilder templateBuilder, Method method) {
        EvCache annotation = method.getAnnotation(EvCache.class);
        if (annotation == null) {
            return;
        }

        Class<? extends EVCacheTranscoder<?>>[] transcoderClasses = annotation.transcoder();
        EVCacheTranscoder<?> transcoder;
        if (transcoderClasses.length == 0) {
            transcoder = null;
        } else if (transcoderClasses.length > 1) {
            throw new ProxyAnnotationException("Multiple transcoders defined on method " + method.getName());
        } else {
            transcoder = Utils.newInstance(transcoderClasses[0]);
        }

        EvCacheOptions evCacheOptions = new EvCacheOptions(
                annotation.appName(),
                annotation.name(),
                annotation.enableZoneFallback(),
                annotation.ttl(),
                transcoder,
                annotation.key());
        if (evCacheOptions != null) {
            CacheId cacheId = new CacheId(evCacheOptions.getAppName(), evCacheOptions.getCacheName());
            EvCacheProvider<?> provider = evCacheProviderPool.get(cacheId);
            if (provider == null) {
                provider = new EvCacheProvider(evCacheOptions);
                evCacheProviderPool.put(cacheId, provider);
            }
            templateBuilder.withCacheProvider(evCacheOptions.getCacheKeyTemplate(), provider);
        }

    }

    @Override
    public void process(String groupName, GroupBuilder groupBuilder, RibbonResourceFactory factory, Class<?> interfaceClass) {
    }
}
