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

package com.netflix.ribbonclientextensions.proxy.test;

import com.netflix.ribbonclientextensions.proxy.Movie;
import com.netflix.ribbonclientextensions.proxy.Recommendations;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RecommendationsTest {

    @Test
    public void testStringParsing() throws Exception {
        Recommendations recommendations = new Recommendations(new Movie[]{
                Movie.ORANGE_IS_THE_NEW_BLACK,
                Movie.BRAKING_BAD
        });
        Recommendations fromString = Recommendations.from(recommendations.toString());
        assertEquals(recommendations, fromString);
    }
}