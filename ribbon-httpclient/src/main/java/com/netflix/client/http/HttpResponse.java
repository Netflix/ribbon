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

import com.google.common.reflect.TypeToken;
import com.netflix.client.IResponse;

import java.io.Closeable;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

/**
 * Response for HTTP communication.
 * 
 * @author awang
 *
 */
public interface HttpResponse extends IResponse, Closeable {
    /**
     * Get the HTTP status code.
     */
    public int getStatus();
    
    /**
     * Get the reason phrase of HTTP status
     */
    public String getStatusLine();
    
    /**
     * @see #getHttpHeaders()
     */
    @Override
    @Deprecated
    public Map<String, Collection<String>> getHeaders();
    
    public HttpHeaders getHttpHeaders();

    public void close();
    
    public InputStream getInputStream();

    public boolean hasEntity();
    
    public <T> T getEntity(Class<T> type) throws Exception;

    public <T> T getEntity(Type type) throws Exception;

    /**
     * @deprecated use {@link #getEntity(Type)}  
     */
    @Deprecated
    public <T> T getEntity(TypeToken<T> type) throws Exception;
}
