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
package com.netflix.client.config;


import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * Created by awang on 7/18/14.
 */
public interface ClientConfigFactory {
    IClientConfig newConfig();

    ClientConfigFactory DEFAULT = findDefaultConfigFactory();

    default int getPriority() { return 0; }

    static ClientConfigFactory findDefaultConfigFactory() {
        return StreamSupport.stream(ServiceLoader.load(ClientConfigFactory.class).spliterator(), false)
                .sorted(Comparator
                        .comparingInt(ClientConfigFactory::getPriority)
                        .thenComparing(f -> f.getClass().getCanonicalName())
                        .reversed())
                .findFirst()
                .orElseGet(() -> {
                    throw new IllegalStateException("Expecting at least one implementation of ClientConfigFactory discoverable via the ServiceLoader");
                });
    }
}
