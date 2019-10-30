package com.netflix.ribbon.transport.netty.http;

import com.netflix.loadbalancer.reactive.ExecutionContext;
import com.netflix.loadbalancer.reactive.ExecutionInfo;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.Server;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Allen Wang
 */
public class TestExecutionListener<I, O> implements ExecutionListener<HttpClientRequest<I>, HttpClientResponse<O>> {

    public AtomicInteger executionStartCounter = new AtomicInteger(0);
    public AtomicInteger startWithServerCounter = new AtomicInteger(0);
    public AtomicInteger exceptionWithServerCounter = new AtomicInteger(0);
    public AtomicInteger executionFailedCounter = new AtomicInteger(0);
    public AtomicInteger executionSuccessCounter = new AtomicInteger(0);

    private HttpClientRequest<ByteBuf> expectedRequest;
    private IClientConfig requestConfig;
    private volatile boolean checkContext = true;
    private volatile boolean checkExecutionInfo = true;
    private volatile Throwable finalThrowable;
    private HttpClientResponse<O> response;
    private List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();
    private AtomicInteger numAttemptsOnServer = new AtomicInteger();
    private AtomicInteger numServers = new AtomicInteger();
    private volatile Server lastServer;
    private static final Integer MY_OBJECT = Integer.valueOf(9);
    private volatile ExecutionContext<HttpClientRequest<I>> context;
    
    public TestExecutionListener(HttpClientRequest<ByteBuf> expectedRequest, IClientConfig requestConfig) {
        this.expectedRequest = expectedRequest;
        this.requestConfig = requestConfig;
    }

    private void checkContext(ExecutionContext<HttpClientRequest<I>> context) {
        try {
            assertSame(requestConfig, context.getRequestConfig());
            assertSame(expectedRequest, context.getRequest());
            assertEquals(MY_OBJECT, context.get("MyObject"));
            if (this.context == null) {
                this.context = context;
            } else {
                assertSame(this.context, context);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            checkContext = false;
        }
    }

    private void checkExecutionInfo(ExecutionInfo info) {
        try {
            assertEquals(numAttemptsOnServer.get(), info.getNumberOfPastAttemptsOnServer());
            assertEquals(numServers.get(), info.getNumberOfPastServersAttempted());
        } catch (Throwable e) {
            e.printStackTrace();
            checkExecutionInfo = false;
        }
    }

    @Override
    public void onExecutionStart(ExecutionContext<HttpClientRequest<I>> context) {
        context.put("MyObject", MY_OBJECT);
        checkContext(context);
        executionStartCounter.incrementAndGet();
    }

    @Override
    public void onStartWithServer(ExecutionContext<HttpClientRequest<I>> context, ExecutionInfo info) {
        checkContext(context);
        
        if (lastServer == null) {
            lastServer = info.getServer();
        } else if (!lastServer.equals(info.getServer())) {
            lastServer = info.getServer();
            numAttemptsOnServer.set(0);
            numServers.incrementAndGet();
        }
        checkExecutionInfo(info);
        startWithServerCounter.incrementAndGet();
    }

    @Override
    public void onExceptionWithServer(ExecutionContext<HttpClientRequest<I>> context, Throwable exception, ExecutionInfo info) {
        checkContext(context);
        checkExecutionInfo(info);
        numAttemptsOnServer.incrementAndGet();
        errors.add(exception);
        exceptionWithServerCounter.incrementAndGet();
    }

    @Override
    public void onExecutionSuccess(ExecutionContext<HttpClientRequest<I>> context, HttpClientResponse<O> response, ExecutionInfo info) {
        checkContext(context);
        checkExecutionInfo(info);
        this.response = response;
        executionSuccessCounter.incrementAndGet();
    }

    @Override
    public void onExecutionFailed(ExecutionContext<HttpClientRequest<I>> context, Throwable finalException, ExecutionInfo info) {
        checkContext(context);
        checkExecutionInfo(info);
        executionFailedCounter.incrementAndGet();
        finalThrowable = finalException;
    }

    public boolean isContextChecked() {
        return checkContext;
    }

    public boolean isCheckExecutionInfo() {
        return checkExecutionInfo;
    }

    public Throwable getFinalThrowable() {
        return finalThrowable;
    }

    public HttpClientResponse<O> getResponse() {
        return response;
    }

    public ExecutionContext<HttpClientRequest<I>> getContext() {
        return this.context;
    }

    @Override
    public String toString() {
        return "TestExecutionListener{" +
                "executionStartCounter=" + executionStartCounter +
                ", startWithServerCounter=" + startWithServerCounter +
                ", exceptionWithServerCounter=" + exceptionWithServerCounter +
                ", executionFailedCounter=" + executionFailedCounter +
                ", executionSuccessCounter=" + executionSuccessCounter +
                ", expectedRequest=" + expectedRequest +
                ", requestConfig=" + requestConfig +
                ", checkContext=" + checkContext +
                ", checkExecutionInfo=" + checkExecutionInfo +
                ", finalThrowable=" + finalThrowable +
                ", response=" + response +
                ", errors=" + errors +
                ", numAttemptsOnServer=" + numAttemptsOnServer +
                ", numServers=" + numServers +
                ", lastServer=" + lastServer +
                ", context=" + context +
                '}';
    }
}
