/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.ribbon.proxy;

import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.evache.EvCacheOptions;
import com.netflix.ribbon.evache.EvCacheProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
class EvCacheProviderPool {

    private final Map<CacheId, EvCacheProvider<?>> pool;

    EvCacheProviderPool(MethodTemplate[] methodTemplates) {
        pool = createEvCachePool(methodTemplates);
    }

    public CacheProvider<?> getMatching(EvCacheOptions evCacheOptions) {
        return pool.get(new CacheId(evCacheOptions.getAppName(), evCacheOptions.getCacheName()));
    }

    private static Map<CacheId, EvCacheProvider<?>> createEvCachePool(MethodTemplate[] methodTemplates) {
        Map<CacheId, EvCacheProvider<?>> newPool = new HashMap<CacheId, EvCacheProvider<?>>();
        for (MethodTemplate template : methodTemplates) {
            EvCacheOptions options = template.getEvCacheOptions();
            if (options != null) {
                CacheId id = new CacheId(options.getAppName(), options.getCacheName());
                if (!newPool.containsKey(id)) {
                    newPool.put(id, new EvCacheProvider(options));
                }
            }
        }
        return newPool;
    }

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
}
