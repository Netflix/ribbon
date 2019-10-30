package com.netflix.client.config;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Internal abstraction to decouple the property source from Ribbon's internal configuration.
 */
public interface PropertyResolver {
    /**
     * @return Get the value of a property or Optional.empty() if not set
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Iterate through all properties with the specified prefix
     */
    void forEach(String prefix, BiConsumer<String, String> consumer);

    /**
     * Provide action to invoke when config changes
     * @param action
     */
    void onChange(Runnable action);
}
