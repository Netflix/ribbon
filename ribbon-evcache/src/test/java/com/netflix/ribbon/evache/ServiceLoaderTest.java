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

import com.netflix.ribbon.proxy.processor.AnnotationProcessor;
import com.netflix.ribbon.proxy.processor.AnnotationProcessorsProvider;
import com.netflix.ribbon.proxy.processor.EVCacheAnnotationProcessor;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertTrue;

/**
 * @author Allen Wang
 */
public class ServiceLoaderTest {
    @Test
    public void testServiceLoader() {
        AnnotationProcessorsProvider annotations = AnnotationProcessorsProvider.DEFAULT;
        List<AnnotationProcessor> processors = annotations.getProcessors();
        boolean hasEVCacheProcessor = false;
        for (AnnotationProcessor processor: processors) {
            Class<?> clazz = processor.getClass();
            if (clazz.equals(EVCacheAnnotationProcessor.class)) {
                hasEVCacheProcessor = true;
                break;
            }
        }
        assertTrue(hasEVCacheProcessor);
    }
}
