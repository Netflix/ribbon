/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.ribbon.transport.netty;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.config.ConfigurationManager;

public class DynamicPropertyBasedPoolStrategyTest {
    @Test
    public void testResize() {
        ConfigurationManager.getConfigInstance().setProperty("foo", "150");
        DynamicPropertyBasedPoolStrategy strategy = new DynamicPropertyBasedPoolStrategy(100, "foo");
        assertEquals(150, strategy.getMaxConnections());
        ConfigurationManager.getConfigInstance().setProperty("foo", "200");
        assertEquals(200, strategy.getMaxConnections());
        ConfigurationManager.getConfigInstance().setProperty("foo", "50");
        assertEquals(50, strategy.getMaxConnections());
    }

}
