package com.netflix.niws;

import com.netflix.niws.client.NiwsClientConfig;

public interface VipAddressResolver {
    public String resolve(String vipAddress, NiwsClientConfig niwsClientConfig);
}
