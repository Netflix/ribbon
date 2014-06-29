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
import com.netflix.evcache.EVCacheImpl;
import com.netflix.evcache.EVCacheTranscoder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import rx.Notification;
import rx.Observable;

import java.util.concurrent.Future;

import static junit.framework.Assert.*;
import static org.easymock.EasyMock.*;
import static org.powermock.api.easymock.PowerMock.*;

/**
 * @author Tomasz Bak
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EVCache.Builder.class, EVCacheImpl.class})
public class EvCacheProviderTest {

    @Mock
    private EVCacheImpl evCacheImplMock;

    @Mock
    private Future<String> cacheFutureMock;

    @Mock
    private EVCacheTranscoder<String> transcoderMock;

    @Before
    public void setUp() throws Exception {
        PowerMock.mockStatic(EVCacheImpl.class);
        expectNew(EVCacheImpl.class,
                new Class[]{String.class, String.class, int.class, EVCacheTranscoder.class, boolean.class},
                anyObject(String.class), anyObject(String.class), anyInt(), anyObject(EVCacheTranscoder.class), anyBoolean()
        ).andReturn(evCacheImplMock);
    }

    @Test
    public void testAsynchronousAccessFromCache() throws Exception {
        expect(evCacheImplMock.<String>getAsynchronous("test1")).andReturn(cacheFutureMock);
        expect(cacheFutureMock.get()).andReturn("value1");

        replayAll();

        EvCacheOptions options = new EvCacheOptions("testApp", "test-cache", true, 100, null, "test{id}");
        EvCacheProvider<Object> cacheProvider = new EvCacheProvider<Object>(options);
        Observable<Object> cacheValue = cacheProvider.get("test1", null);

        assertEquals("value1", cacheValue.toBlocking().first());
    }

    @Test
    public void testAsynchronousAccessWithTranscoderFromCache() throws Exception {
        expect(evCacheImplMock.getAsynchronous("test1", transcoderMock)).andReturn(cacheFutureMock);
        expect(cacheFutureMock.get()).andReturn("value1");

        replayAll();

        EvCacheOptions options = new EvCacheOptions("testApp", "test-cache", true, 100, transcoderMock, "test{id}");
        EvCacheProvider<Object> cacheProvider = new EvCacheProvider<Object>(options);
        Observable<Object> cacheValue = cacheProvider.get("test1", null);

        assertEquals("value1", cacheValue.toBlocking().first());
    }

    @Test
    public void testFailedAsynchronousAccessFromCache() throws Exception {
        expect(evCacheImplMock.<String>getAsynchronous("test1")).andThrow(new EVCacheException("cache error"));

        replayAll();

        EvCacheOptions options = new EvCacheOptions("testApp", "test-cache", true, 100, null, "test{id}");
        EvCacheProvider<Object> cacheProvider = new EvCacheProvider<Object>(options);
        Observable<Object> cacheValue = cacheProvider.get("test1", null);

        Notification<Object> notification = cacheValue.materialize().toBlocking().first();
        assertTrue(notification.getThrowable() instanceof CacheFaultException);
    }
}