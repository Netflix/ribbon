package com.netflix.ribbon.evache;

import com.netflix.ribbon.proxy.processor.AnnotationProcessor;
import com.netflix.ribbon.proxy.processor.EVCacheAnnotationProcessor;
import com.netflix.ribbon.proxy.processor.ProxyAnnotations;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertTrue;

/**
 * @author Allen Wang
 */
public class ServiceLoaderTest {
    @Test
    public void testServiceLoader() {
        ProxyAnnotations annotations = ProxyAnnotations.getInstance();
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
