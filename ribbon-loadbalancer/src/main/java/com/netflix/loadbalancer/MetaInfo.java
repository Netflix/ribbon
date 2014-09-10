package com.netflix.loadbalancer;

/**
 * @author Allen Wang
 */
public interface MetaInfo {
    public String getAppName();

    public String getScalingGroup();

    public String getVipAddresses();

    public String getInstanceId();
}
