package com.netflix.ribbon.proxy.processor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Tomasz Bak
 */
public class ProxyAnnotations {

    private static final ProxyAnnotations DEFAULT = new ProxyAnnotations();

    private final List<AnnotationProcessor> processors = new CopyOnWriteArrayList<AnnotationProcessor>();

    private ProxyAnnotations() {
        processors.add(new HttpAnnotationProcessor());
        processors.add(new HystrixAnnotationProcessor());
        processors.add(new CacheProviderAnnotationProcessor());
        processors.add(new ClientPropertiesProcessor());
        ServiceLoader<AnnotationProcessor> loader = ServiceLoader.load(AnnotationProcessor.class);
        Iterator<AnnotationProcessor> iterator = loader.iterator();
        Set<AnnotationProcessor> externalProcessors = new HashSet<AnnotationProcessor>();
        while (iterator.hasNext()) {
            externalProcessors.add(iterator.next());
        }
        processors.addAll(externalProcessors);
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
