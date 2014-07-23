package com.netflix.ribbon.guice;

import com.google.inject.Inject;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.RibbonResourceFactory;

/**
 * Guice Provider extension to create a ribbon resource from an injectable
 * RibbonResourceFactory.
 * 
 * @author elandau
 *
 * @param <T>
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
        return factory.from(contract, factory.createHttpResourceGroup(contract.getName(), ClientOptions.create()));
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
    void initialize(RibbonResourceFactory factory) {
        this.factory = factory;
    }
}
