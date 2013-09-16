package com.netflix.client.netty.http;

import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.netflix.client.AsyncLoadBalancingClient;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;

public class LoadBalancingNettyClient extends AsyncLoadBalancingClient<NettyHttpRequest, NettyHttpResponse> {

    public LoadBalancingNettyClient() {
        super(new AsyncNettyHttpClient(new DefaultClientConfigImpl()));
    }
    
    public LoadBalancingNettyClient(IClientConfig config) {
        super(new AsyncNettyHttpClient(config));
        this.initWithNiwsConfig(config);
    }

    @Override
    protected boolean isCircuitBreakerException(Throwable e) {
        while (e != null) {
            if (e instanceof io.netty.handler.timeout.ReadTimeoutException
                    || e instanceof io.netty.channel.ConnectTimeoutException
                    || e instanceof SocketException
                    || e instanceof SocketTimeoutException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    @Override
    protected boolean isRetriableException(Throwable e) {
        return isCircuitBreakerException(e);
    }
}
