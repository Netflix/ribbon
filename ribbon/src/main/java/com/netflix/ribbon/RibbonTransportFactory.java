package com.netflix.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.netty.RibbonTransport;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClient;

/**
 * Created by awang on 7/18/14.
 */
public interface RibbonTransportFactory {
    public HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config);

    public static final RibbonTransportFactory DEFAULT = new RibbonTransportFactory() {
        @Override
        public HttpClient<ByteBuf, ByteBuf> newHttpClient(IClientConfig config) {
            return RibbonTransport.newHttpClient(config);
        }
    };
}
