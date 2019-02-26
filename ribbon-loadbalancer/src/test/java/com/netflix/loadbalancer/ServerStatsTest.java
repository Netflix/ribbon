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
package com.netflix.loadbalancer;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.servo.monitor.Monitors;

public class ServerStatsTest {
    
    @Test
    public void testRegisterWithServo() {
        // Make sure annotations are correct:
        // https://github.com/Netflix/ribbon/issues/191
//        Monitors.registerObject(new ServerStats());
    }
}
