/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.serialization;


/**
 * A {@link SerializationFactory} that uses Jackson for serialization and deserialization. 
 * It returns a {@link JacksonCodec} from the get methods if content-type from Http 
 * headers is "application/json" or "text/event-stream" (for deserialization only). Otherwise it turns null. 
 * The {@link TypeDef} parameter is not used.
 *  
 * @author awang
 *
 */
public class JacksonSerializationFactory implements SerializationFactory<HttpSerializationContext>{

    @Override
    public <T extends Object> Deserializer<T> getDeserializer(HttpSerializationContext key, TypeDef<T> typeDef) {
        if (key.getContentType().equalsIgnoreCase("application/json")
                || key.getContentType().equalsIgnoreCase("text/event-stream")) {
            return JacksonCodec.getInstance();
        }
        return null;
    }

    @Override
    public <T> Serializer<T> getSerializer(HttpSerializationContext key, TypeDef<T> typeDef) {
        if (key.getContentType().equalsIgnoreCase("application/json")) {
            return JacksonCodec.getInstance();
        }
        return null;
    }
}
