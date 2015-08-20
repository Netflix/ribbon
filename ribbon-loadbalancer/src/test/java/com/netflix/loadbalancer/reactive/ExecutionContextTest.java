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
package com.netflix.loadbalancer.reactive;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Allen Wang
 */
public class ExecutionContextTest {
    @Test
    public void testSubContext() {
        ExecutionContext<String> context = new ExecutionContext<String>("hello", DefaultClientConfigImpl.getEmptyConfig(),
                DefaultClientConfigImpl.getClientConfigWithDefaultValues(), RetryHandler.DEFAULT);
        ExecutionContext<String> subContext1 = context.getChildContext("foo");
        ExecutionContext<String> subContext2 = context.getChildContext("bar");
        assertSame(context, context.getGlobalContext());
        context.put("dummy", "globalValue");
        context.put("dummy2", "globalValue");
        subContext1.put("dummy", "context1Value");
        subContext2.put("dummy", "context2Value");
        assertEquals("context1Value", subContext1.get("dummy"));
        assertEquals("context2Value", subContext2.get("dummy"));
        assertEquals("globalValue", subContext1.getGlobalContext().get("dummy"));
        assertNull(subContext1.get("dummy2"));
    }
}
