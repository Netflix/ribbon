package com.netflix.ribbon.proxy.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tomasz Bak
 */
public class ProxyAnnotations {

    private static final ProxyAnnotations DEFAULT = new ProxyAnnotations();

    private final List<AnnotationProcessor> processors = new CopyOnWriteArrayList<AnnotationProcessor>();

    private ProxyAnnotations() {
        // We need direct handling for template name and result type
        // to create http resource template from resource group

        processors.add(new HttpAnnotationProcessor());
        processors.add(new HystrixAnnotationProcessor());
        processors.add(new CacheProviderAnnotationProcessor());
        processors.add(new EVCacheAnnotationProcessor());
    }

    public void register(AnnotationProcessor processor) {
        processors.add(processor);
    }

    public List<AnnotationProcessor> getProcessors() {
        return processors;
    }

    public static final ProxyAnnotations getInstance() {
        return DEFAULT;
    }


}
