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
import com.netflix.ribbon.proxy.EvCacheProviderPool;
import com.netflix.ribbon.proxy.MethodTemplate;
import com.netflix.ribbon.proxy.Utils;
import com.netflix.ribbon.proxy.sample.MovieServiceInterfaces.SampleMovieService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static junit.framework.Assert.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EvCacheProviderPool.class})
public class EvCacheProviderPoolTest {

    @Mock
    private EvCacheProvider evCacheProviderMock;

    @Before
    public void setUp() throws Exception {
        mockStatic(EvCacheProvider.class);
        expectNew(EvCacheProvider.class, new Class[]{EvCacheOptions.class}, anyObject(EvCacheOptions.class)).andReturn(evCacheProviderMock);
    }

    @Test
    public void testCreate() throws Exception {
        replayAll();
        EvCacheProviderPool pool = new EvCacheProviderPool(MethodTemplate.from(SampleMovieService.class));
        MethodTemplate findById = new MethodTemplate(Utils.methodByName(SampleMovieService.class, "findMovieById"));
        CacheProvider<?> cacheProvider = pool.getMatching(findById.getEvCacheOptions());

        assertEquals(evCacheProviderMock, cacheProvider);
    }
}