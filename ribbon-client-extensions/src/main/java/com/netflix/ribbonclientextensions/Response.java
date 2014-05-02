package com.netflix.ribbonclientextensions;

import java.util.Map;

/**
 * Created by mcohen on 4/24/14.
 */
public class Response<Result> {

    Result data;
    Map<String, String> headers;

    public Result getData() {
        return data;
    }
}
