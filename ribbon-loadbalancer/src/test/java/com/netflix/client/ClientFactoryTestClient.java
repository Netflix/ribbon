package com.netflix.client;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import com.netflix.client.ClientFactoryTestClient.TestIResponse;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;

public class ClientFactoryTestClient extends
        AbstractLoadBalancerAwareClient<ClientRequest, TestIResponse> {

    public ClientFactoryTestClient() {
        super(null);
    }

    public ClientFactoryTestClient(ILoadBalancer lb, IClientConfig clientConfig) {
        super(lb, clientConfig);
    }

    @Override
    public TestIResponse execute(ClientRequest request,
            IClientConfig requestConfig) throws Exception {
        return null;
    }

    @Override
    public RequestSpecificRetryHandler getRequestSpecificRetryHandler(
            ClientRequest request, IClientConfig requestConfig) {
        return null;
    }

    protected static class TestIResponse implements IResponse {

        @Override
        public void close() throws IOException {

        }

        @Override
        public Object getPayload() throws ClientException {
            return null;
        }

        @Override
        public boolean hasPayload() {
            return false;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public URI getRequestedURI() {
            return null;
        }

        @Override
        public Map<String, ?> getHeaders() {
            return null;
        }

    }

}
