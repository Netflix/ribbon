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
package com.netflix.ribbon.examples;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.netflix.client.StreamDecoder;

/**
 * A {@link StreamDecoder} used by some sample application. This decoder decodes
 * content of ByteBuffer into list of Server-Sent Event string. 
 * <p>
 * This code is copied from https://github.com/Netflix/RxJava/tree/master/rxjava-contrib/rxjava-apache-http
 *  
 * @author awang
 *
 */
public class SSEDecoder implements StreamDecoder<String, ByteBuffer> {

    public String decode(ByteBuffer input) throws IOException {
        if (input == null || !input.hasRemaining()) {
            return null;
        }
        byte[] buffer = new byte[input.limit()];
        boolean foundDelimiter = false;
        int index = 0;
        int start = input.position();
        while (input.remaining() > 0) {
            byte b = input.get();
            if (b == 10 || b == 13) {
                foundDelimiter = true;
                break;
            } else {
                buffer[index++] = b;
            }
        }
        if (!foundDelimiter) {
            // reset the position so that bytes read so far 
            // will not be lost for next chunk
            input.position(start);
            return null;
        }
        if (index == 0) {
            return null;
        }
        return new String(buffer, 0, index, "UTF-8");
    }        
}
