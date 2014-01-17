package com.netflix.niws.client.http;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.apache.http.ConnectionClosedException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;

import com.google.common.collect.Lists;
import com.netflix.client.ClientException;
import com.netflix.client.LoadBalancerContext;
import com.netflix.client.LoadBalancerErrorHandler;
import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;

public class HttpClientLoadBalancerErrorHandler implements LoadBalancerErrorHandler<HttpRequest, HttpResponse> {

    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class, 
                    NoHttpResponseException.class, ConnectionPoolTimeoutException.class, ConnectionClosedException.class, HttpHostConnectException.class);
    
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class, ConnectionClosedException.class, HttpHostConnectException.class);
    
    /**
     * @return true if request is retriable and the Throwable has any of the following type of exception as a cause: 
     * {@link ConnectException}, {@link SocketTimeoutException}, {@link NoHttpResponseException}, {@link ConnectionPoolTimeoutException}
     * 
     */
    @Override
    public boolean isRetriableException(HttpRequest request, Throwable e,
            boolean sameServer) {
        if (request.isRetriable() && LoadBalancerContext.isPresentAsCause(e, retriable)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if the Throwable has one of the following exception type as a cause: 
     * {@link SocketException}, {@link SocketTimeoutException}
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        if (e instanceof ClientException) {
            return ((ClientException) e).getErrorType() == ClientException.ErrorType.SERVER_THROTTLED;
        }

        return LoadBalancerContext.isPresentAsCause(e, circuitRelated);
    }

    /**
     * @return true if the the response has status code 503 (throttle) 
     */
    @Override
    public boolean isCircuitTrippinResponse(Object response) {
        if (!(response instanceof HttpResponse)) {
            return false;
        }
        return ((HttpResponse) response).getStatus() == 503;
    }
}
