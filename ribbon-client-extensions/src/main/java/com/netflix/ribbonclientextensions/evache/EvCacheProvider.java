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

package com.netflix.ribbonclientextensions.evache;

import com.netflix.evcache.EVCache;
import com.netflix.evcache.EVCacheException;
import com.netflix.ribbonclientextensions.CacheProvider;
import rx.Observable;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Tomasz Bak
 */
public class EvCacheProvider<T> implements CacheProvider<T> {

    private final EvCacheOptions options;
    private final EVCache evCache;

    public EvCacheProvider(EvCacheOptions options) {
        this.options = options;
        EVCache.Builder builder = new EVCache.Builder();
        if (options.isEnableZoneFallback()) {
            builder.enableZoneFallback();
        }
        builder.setDefaultTTL(options.getTimeToLive());
        builder.setAppName(options.getAppName());
        builder.setCacheName(options.getCacheName());
        evCache = builder.build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Observable<T> get(String key, Map<String, Object> requestProperties) {
        Future<T> getFuture;
        try {
            if (options.getTranscoder() == null) {
                getFuture = evCache.getAsynchronous(key);
            } else {
                getFuture = (Future<T>) evCache.getAsynchronous(key, options.getTranscoder());
            }
        } catch (EVCacheException e) {
            return Observable.error(new CacheFaultException("EVCache exception when getting value for key " + key, e));
        }
        return Observable.from(getFuture);
    }
}
