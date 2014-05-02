package com.netflix.ribbonclientextensions.hystrix;

import rx.Observable;

import java.util.Map;

/**
 * Created by mcohen on 4/22/14.
 */
public class FallbackResponse<T> {
    Observable<T> content;
    int status_code = 200;
    Map<String, String> responseHeaders;

    public FallbackResponse(Observable<T> content){
        this.content = content;
    }

    public FallbackResponse(Observable<T> content, int status_code){
        this.content = content;
        this.status_code = status_code;
    }

    public FallbackResponse(Observable<T> content, int status_code, Map<String, String> responseHeaders) {
        this.content = content;
        this.status_code = status_code;
        this.responseHeaders = responseHeaders;
    }

    public void setContent(Observable<T> content) {
        this.content = content;
    }

    public void setStatus_code(int status_code) {
        this.status_code = status_code;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public Observable<T> getContent() {

        return content;
    }

    public int getStatus_code() {
        return status_code;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }
}
