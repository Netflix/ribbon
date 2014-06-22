package com.netflix.ribbonclientextensions;

public interface ResourceGroup {
    String name();

    <T extends RequestTemplate<?, ?>> RequestTemplateBuilder<T> requestTemplateBuilder();
    
    public abstract class RequestTemplateBuilder<T extends RequestTemplate<?, ?>> {
        public abstract <S> T newRequestTemplate(String name, Class<? extends S> classType);
    }
}
