package com.netflix.ribbon.proxy.processor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;

/**
 * @author Tomasz Bak
 */
public class ProxyAnnotations {

    private static final ProxyAnnotations DEFAULT = new ProxyAnnotations();

    private final List<AnnotationProcessor> processors = new CopyOnWriteArrayList<AnnotationProcessor>();

    public ProxyAnnotations() {
        // We need direct handling for template name and result type
        // to create http resource template from resource group

        processors.add(new HttpAnnotationProcessor());
    }

    public void register(AnnotationProcessor processor) {
        processors.add(processor);
    }

    public <T> TemplateFactory<T> templateFor(Method method) {
        final TemplateConfigurator configurator = process(method);
        return new TemplateFactory<T>() {
            @Override
            public HttpRequestTemplate<T> createTemplate() {
                // Create resource group from template
                HttpResourceGroup resourceGroup = null;

                // Create template from resource group
                HttpRequestTemplate<T> httpRequestTemplate = null;

                // Configure it
                configurator.configure(httpRequestTemplate);

                return httpRequestTemplate;
            }
        };
    }

    public static ProxyAnnotations getInstance() {
        return DEFAULT;
    }

    private TemplateConfigurator process(Method method) {
        final List<TemplateConfigurator> configurators = new ArrayList<TemplateConfigurator>();
        for (AnnotationProcessor processor : processors) {
            configurators.add(processor.process(method));
        }
        return new TemplateConfigurator() {
            @Override
            public void configure(HttpRequestTemplate template) {
                for (TemplateConfigurator configurator : configurators) {
                    configurator.configure(template);
                }
            }
        };
    }
}
