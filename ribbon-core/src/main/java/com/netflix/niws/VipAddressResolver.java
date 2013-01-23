package com.netflix.niws;

import com.netflix.niws.client.IClientConfig;

public interface VipAddressResolver {
    public String resolve(String vipAddress, IClientConfig niwsClientConfig);
}
