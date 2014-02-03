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

import com.netflix.client.ResponseCallback;
import com.netflix.client.StreamDecoder;

/**
 * A convenient interface for HTTP response callback to hide the generic types from
 * its parent interface.
 * 
 * @author awang
 *
 * @param <T> Type of of object that can be formed from partial 
 *             content in the native stream. See {@link StreamDecoder}.
 */
public interface HttpResponseCallback<T> extends ResponseCallback<HttpResponse, T> {
}
