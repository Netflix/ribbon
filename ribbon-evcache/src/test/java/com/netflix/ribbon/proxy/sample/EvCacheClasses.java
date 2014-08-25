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

package com.netflix.ribbon.proxy.sample;

import com.netflix.evcache.EVCacheTranscoder;
import net.spy.memcached.CachedData;

/**
 * @author Tomasz Bak
 */
public class EvCacheClasses {

    public static class SampleEVCacheTranscoder implements EVCacheTranscoder<Object> {

        @Override
        public boolean asyncDecode(CachedData d) {
            return false;
        }

        @Override
        public CachedData encode(Object o) {
            return null;
        }

        @Override
        public Object decode(CachedData d) {
            return null;
        }

        @Override
        public int getMaxSize() {
            return 0;
        }
    }
}
