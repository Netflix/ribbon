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
package com.netflix.client.http;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import com.netflix.client.ResponseWithTypedEntity;
import com.netflix.serialization.TypeDef;

/**
 * Response for HTTP communication.
 * 
 * @author awang
 *
 */
public interface HttpResponse extends ResponseWithTypedEntity, Closeable {
    /**
     * Get the HTTP status code.
     */
    public int getStatus();
    
    public String getStatusLine();
    
    @Override
    @Deprecated
    public Map<String, Collection<String>> getHeaders();
    
    public HttpHeaders getHttpHeaders();

    public void close();
    
    public InputStream getInputStream();
    
    @Deprecated
    public <T> T getEntity(Class<T> type) throws Exception;
    
    @Deprecated
    public <T> T getEntity(TypeDef<T> type) throws Exception;
}
