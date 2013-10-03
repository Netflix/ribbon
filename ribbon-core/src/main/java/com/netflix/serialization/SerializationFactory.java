package com.netflix.serialization;

import com.google.common.base.Optional;


public interface SerializationFactory<K extends Object> {
    public Optional<Deserializer> getDeserializer(K key);  
    public Optional<Serializer> getSerializer(K key);
}
