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
package com.netflix.ribbon;

import java.util.Map;

import com.netflix.ribbon.RequestTemplate.RequestBuilder;

import rx.Observable;

public interface CacheProvider<T> {
    /**
     * @param keyTemplate A key template which may contain variable, e.g., /foo/bar/{id},
     *          where the variable will be substituted with real value by {@link RequestBuilder}
     *          
     * @param requestProperties Key value pairs provided via {@link RequestBuilder#withRequestProperty(String, Object)}
     *  
     * @return Cache content as a lazy {@link Observable}. It is assumed that
     *      the actual cache retrieval does not happen until the returned {@link Observable}
     *      is subscribed to.
     */
    Observable<T> get(String keyTemplate, Map<String, Object> requestProperties);
}
