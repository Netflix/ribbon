package com.netflix.client.netty;

import com.netflix.config.DynamicProperty;

import io.reactivex.netty.client.MaxConnectionsBasedStrategy;

/**
 * A {@link MaxConnectionsBasedStrategy} that resize itself based on callbacks from
 * {@link DynamicProperty}
 * 
 * @author awang
 *
 */
public class DynamicPropertyBasedPoolStrategy extends MaxConnectionsBasedStrategy {

    private final DynamicProperty poolSizeProperty;
    
    public DynamicPropertyBasedPoolStrategy(final int maxConnections, String propertyName) {
        super(maxConnections);
        poolSizeProperty = DynamicProperty.getInstance(propertyName);
        setMaxConnections(poolSizeProperty.getInteger(maxConnections));
        poolSizeProperty.addCallback(new Runnable() {
            @Override
            public void run() {
                setMaxConnections(poolSizeProperty.getInteger(maxConnections));
            };
        });
    }
    
    protected void setMaxConnections(int max) {
        int diff = max - getMaxConnections();
        incrementMaxConnections(diff);
    }
}
