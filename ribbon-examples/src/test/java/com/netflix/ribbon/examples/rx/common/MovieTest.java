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

import org.junit.Test;

import static com.netflix.ribbon.examples.rx.common.Movie.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class MovieTest {

    @Test
    public void testStringParsing() {
        Movie fromString = Movie.from(ORANGE_IS_THE_NEW_BLACK.toString());
        assertEquals(ORANGE_IS_THE_NEW_BLACK, fromString);
    }
}
