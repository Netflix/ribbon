package com.netflix.http4;

public class NFHttpClientConstants {

    public static final boolean DEFAULT_CONNECTIONIDLE_TIMETASK_ENABLED = false;
    
    public static final int DEFAULT_CONNECTION_IDLE_TIMERTASK_REPEAT_IN_MSECS = 30*1000; // every half minute (30 secs)
    
    public static final int DEFAULT_CONNECTIONIDLE_TIME_IN_MSECS = 30*1000; // all connections idle for 30 secs
    
    public static final int DEFAULT_CONNECTION_MAXAGE_IN_MSECS = 5*60*1000; // max age 
    
}
