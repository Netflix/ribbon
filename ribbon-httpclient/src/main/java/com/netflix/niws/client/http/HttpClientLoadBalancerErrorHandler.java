package com.netflix.niws.client.http;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

import org.apache.http.ConnectionClosedException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;

import com.google.common.collect.Lists;
import com.netflix.client.ClientException;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.http.HttpResponse;

public class HttpClientLoadBalancerErrorHandler extends DefaultLoadBalancerRetryHandler {

    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class, ConnectTimeoutException.class,
                    NoHttpResponseException.class, ConnectionPoolTimeoutException.class, ConnectionClosedException.class, HttpHostConnectException.class);
    
    @SuppressWarnings("unchecked")
    protected List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class, ConnectTimeoutException.class, 
                    ConnectionClosedException.class, HttpHostConnectException.class);
    
    public HttpClientLoadBalancerErrorHandler() {
        super();
    }

    public HttpClientLoadBalancerErrorHandler(IClientConfig clientConfig) {
        super(clientConfig);
    }

    public HttpClientLoadBalancerErrorHandler(int retrySameServer,
            int retryNextServer, boolean retryEnabled) {
        super(retrySameServer, retryNextServer, retryEnabled);
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

        return super.isCircuitTrippingException(e);
    }


    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        if (e instanceof ClientException) {
            ClientException ce = (ClientException) e;
            if (ce.getErrorType() == ClientException.ErrorType.SERVER_THROTTLED) {
                return !sameServer && retryEnabled;
            }
        }
        return super.isRetriableException(e, sameServer);
    }

    @Override
    protected List<Class<? extends Throwable>> getRetriableExceptions() {
        return retriable;
    }
    
    @Override
    protected List<Class<? extends Throwable>>  getCircuitRelatedExceptions() {
        return circuitRelated;
    }

}
