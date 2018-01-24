package com.netflix.loadbalancer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author:HuangY
 * @create 2018-01-24 上午10:36
 **/
public class WeightedResponseTimeRuleTest {
    @Test
    public void testBatcth(){
        for(int i=0;i<5;i++){
            test();
        }
    }
    @Test
    public void test(){
        WeightedResponseTimeRule weightedResponseTimeRule = new WeightedResponseTimeRule();
        ILoadBalancer iLoadBalancer = new BaseLoadBalancer();
        List<Server> newServerList = new ArrayList<>(3);
        newServerList.add(new Server("127.0.0.1:80"));
        newServerList.add(new Server("127.0.0.1:81"));
//        newServerList.add(new Server("127.0.0.1:83"));
        iLoadBalancer.addServers(newServerList);
        weightedResponseTimeRule.setLoadBalancer(iLoadBalancer);
        List<Double> weights = new ArrayList<>(3);
        weights.add(1d);
        weights.add(3d);
        weights.add(4d);
        weightedResponseTimeRule.setWeights(weights);

        Server server = weightedResponseTimeRule.choose(iLoadBalancer,null);
        System.out.println(server);
    }
}
