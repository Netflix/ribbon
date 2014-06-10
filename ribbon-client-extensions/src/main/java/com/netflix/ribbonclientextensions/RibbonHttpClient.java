package com.netflix.ribbonclientextensions;

public class RibbonHttpClient<I, O> implements RibbonClient<I, O, HttpRequestTemplate<I, O>> {

    @Override
    public HttpRequestTemplate<I, O> newRequestTemplate() {
        return null;
    }
}
