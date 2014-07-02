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

package com.netflix.ribbon.evache;

import com.netflix.evcache.EVCacheTranscoder;

/**
 * @author Tomasz Bak
 */
public class EvCacheOptions {
    private final String appName;
    private final String cacheName;
    private final boolean enableZoneFallback;
    private final int timeToLive;
    private final EVCacheTranscoder<?> transcoder;
    private final String cacheKeyTemplate;

    public EvCacheOptions(String appName, String cacheName, boolean enableZoneFallback, int timeToLive,
                          EVCacheTranscoder<?> transcoder, String cacheKeyTemplate) {
        this.appName = appName;
        this.cacheName = cacheName;
        this.enableZoneFallback = enableZoneFallback;
        this.timeToLive = timeToLive;
        this.transcoder = transcoder;
        this.cacheKeyTemplate = cacheKeyTemplate;
    }

    public String getAppName() {
        return appName;
    }

    public String getCacheName() {
        return cacheName;
    }

    public boolean isEnableZoneFallback() {
        return enableZoneFallback;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public EVCacheTranscoder<?> getTranscoder() {
        return transcoder;
    }

    public String getCacheKeyTemplate() {
        return cacheKeyTemplate;
    }
}
