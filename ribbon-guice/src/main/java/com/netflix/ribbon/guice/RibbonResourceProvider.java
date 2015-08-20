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
package com.netflix.ribbon.guice;

import com.google.inject.Inject;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.netflix.ribbon.RibbonResourceFactory;

/**
 * Guice Provider extension to create a ribbon resource from an injectable
 * RibbonResourceFactory.
 * 
 * @author elandau
 *
 * @param <T> Type of the client API interface class
 */
public class RibbonResourceProvider<T> implements ProviderWithExtensionVisitor<T> {

    private RibbonResourceFactory factory;
    private final Class<T> contract;
    
    public RibbonResourceProvider(Class<T> contract) {
        this.contract = contract;
    }
    
    @Override
    public T get() {
        // TODO: Get name from annotations (not only class name of contract)
        return factory.from(contract);
    }

    /**
     * This is needed for 'initialize(injector)' below to be called so the provider
     * can get the injector after it is instantiated.
     */
    @Override
    public <B, V> V acceptExtensionVisitor(
            BindingTargetVisitor<B, V> visitor,
            ProviderInstanceBinding<? extends B> binding) {
        return visitor.visit(binding);
    }

    @Inject
    @Toolable
    protected void initialize(RibbonResourceFactory factory) {
        this.factory = factory;
    }
}
