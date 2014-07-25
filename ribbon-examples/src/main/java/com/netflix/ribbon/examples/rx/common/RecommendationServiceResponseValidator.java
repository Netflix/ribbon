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

package com.netflix.ribbon.examples.rx.common;

import com.netflix.ribbon.ServerError;
import com.netflix.ribbon.UnsuccessfulResponseException;
import com.netflix.ribbon.http.HttpResponseValidator;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

/**
 * @author Tomasz Bak
 */
public class RecommendationServiceResponseValidator implements HttpResponseValidator {
    @Override
    public void validate(HttpClientResponse<ByteBuf> response) throws UnsuccessfulResponseException, ServerError {
        if (response.getStatus().code() / 100 != 2) {
            throw new UnsuccessfulResponseException("Unexpected HTTP status code " + response.getStatus());
        }
    }
}
