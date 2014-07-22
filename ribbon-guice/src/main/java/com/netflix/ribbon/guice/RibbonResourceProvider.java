package com.netflix.ribbon.guice;

import com.google.inject.Inject;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.netflix.ribbon.HttpResourceGroupFactory;

/**
 * Guice Provider extension to create a ribbon resource from an injectable
 * RibbonResourceFactory.
 * 
 * @author elandau
 *
 * @param <T>
 */
public class RibbonResourceProvider<T> implements ProviderWithExtensionVisitor<T> {

    private HttpResourceGroupFactory factory;
    private final Class<T> contract;
    
    public RibbonResourceProvider(Class<T> contract) {
        this.contract = contract;
    }
    
    @Override
    public T get() {
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
    void initialize(HttpResourceGroupFactory factory) {
        this.factory = factory;
    }
}
