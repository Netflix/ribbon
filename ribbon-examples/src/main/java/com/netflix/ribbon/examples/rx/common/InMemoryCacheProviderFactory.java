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

package com.netflix.ribbon.examples.rx.common;

import com.netflix.ribbon.CacheProvider;
import com.netflix.ribbon.CacheProviderFactory;
import rx.Observable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tomasz Bak
 */
public class InMemoryCacheProviderFactory implements CacheProviderFactory<Movie> {

    @Override
    public CacheProvider<Movie> createCacheProvider() {
        return new InMemoryCacheProvider();
    }

    public static class InMemoryCacheProvider implements CacheProvider<Movie> {
        private final Map<String, Object> cacheMap = new ConcurrentHashMap<String, Object>();

        @Override
        public Observable<Movie> get(String key, Map<String, Object> requestProperties) {
            return null;
        }
    }
}
