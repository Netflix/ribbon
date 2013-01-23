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
package com.netflix.http4;

public class NFHttpClientConstants {

    public static final boolean DEFAULT_CONNECTIONIDLE_TIMETASK_ENABLED = false;
    
    public static final int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = 30*1000; // every half minute (30 secs)
    
    public static final int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = 30*1000; // all connections idle for 30 secs
    
    public static final int DEFAULT_CONNECTION_MAXAGE_IN_MSECS = 5*60*1000; // max age 
    
}
