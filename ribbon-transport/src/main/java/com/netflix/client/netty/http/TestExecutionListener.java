package com.netflix.client.netty.http;

import com.netflix.client.ExecutionContext;
import com.netflix.client.ExecutionInfo;
import com.netflix.client.ExecutionListener;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

/**
 * @author Allen Wang
 */
public class TestExecutionListener implements ExecutionListener<HttpClientRequest, HttpClientResponse> {
    @Override
    public void onExecutionStart(ExecutionContext<HttpClientRequest> context) {
        System.err.println("onExecutionStart");
    }

    @Override
    public void onStartWithServer(ExecutionContext<HttpClientRequest> context, ExecutionInfo info) {
        System.err.println("onStartWithServer: " + info);
    }

    @Override
    public void onExceptionWithServer(ExecutionContext<HttpClientRequest> context, Throwable exception, ExecutionInfo info) {
        System.err.println("onExceptionWithServer: " + info);
    }

    @Override
    public void onExecutionSuccess(ExecutionContext<HttpClientRequest> context, HttpClientResponse response, ExecutionInfo info) {
        System.err.println("onExecutionSuccess: " + info);
    }

    @Override
    public void onExecutionFailed(ExecutionContext<HttpClientRequest> context, Throwable finalException, ExecutionInfo info) {
        System.err.println("onExecutionFailed: " + info);
    }
}
