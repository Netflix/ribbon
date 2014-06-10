package com.netflix.ribbonclientextensions;


/**
 * 
 * @author awang
 *
 * @param <I> Request input entity type
 * @param <O> Response entity type
 * @param <T>
 */
public interface RibbonClient<I, O, T extends RequestTemplate<I, O, ?>> {
    
    public T newRequestTemplate();
}
