package com.netflix.loadbalancer;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import com.google.common.collect.Lists;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;

public class LoadBalancerCommandTest {

    static Server server1 = new Server("1", 80);
    static Server server2 = new Server("2", 80);
    static Server server3 = new Server("3", 80);
    
    static List<Server> list = Lists.newArrayList(server1, server2, server3);

    @Test
    public void testRetrySameServer() {
        BaseLoadBalancer loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(list);
        RetryHandler handler = new RetryHandler() {
            @Override
            public boolean isRetriableException(Throwable e, boolean sameServer) {
                return (e instanceof IllegalArgumentException);
            }
            @Override
            public boolean isCircuitTrippingException(Throwable e) {
                return false;
            }
            @Override
            public int getMaxRetriesOnSameServer() {
                return 3;
            }
            @Override
            public int getMaxRetriesOnNextServer() {
                return 0;
            }
        };

        LoadBalancerCommand<String> command = LoadBalancerCommand.<String>builder()
                .withLoadBalancer(loadBalancer)
                .withRetryHandler(handler)
                .withServer(server1)
                .build();
        
        ServerOperation<String> operation = new ServerOperation<String>() {
            AtomicInteger count = new AtomicInteger();
            @Override
            public Observable<String> call(final Server server) {
                return Observable.create(new OnSubscribe<String>(){
                    @Override
                    public void call(Subscriber<? super String> t1) {
                        if (count.incrementAndGet() < 3) {
                            t1.onError(new IllegalArgumentException());
                        } else {
                            t1.onNext(server.getHost());
                            t1.onCompleted();
                        }
                    }
                });
            }
        };
        
        String result = command.submit(operation).toBlocking().single();
        assertEquals(3, loadBalancer.getLoadBalancerStats().getSingleServerStat(server1).getTotalRequestsCount());
        assertEquals("1", result);
    }

    @Test
    public void testRetryNextServer() {
        BaseLoadBalancer loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(list);
        RetryHandler handler = new RetryHandler() {
            @Override
            public boolean isRetriableException(Throwable e, boolean sameServer) {
                return (e instanceof IllegalArgumentException);
            }
            @Override
            public boolean isCircuitTrippingException(Throwable e) {
                return false;
            }
            @Override
            public int getMaxRetriesOnSameServer() {
                return 1;
            }
            @Override
            public int getMaxRetriesOnNextServer() {
                return 5;
            }
        };
        ServerOperation<String> operation = new ServerOperation<String>() {
            AtomicInteger count = new AtomicInteger();
            @Override
            public Observable<String> call(final Server server) {
                return Observable.create(new OnSubscribe<String>(){
                    @Override
                    public void call(Subscriber<? super String> t1) {
                        if (count.incrementAndGet() < 3) {
                            t1.onError(new IllegalArgumentException());
                        } else {
                            t1.onNext(server.getHost());
                            t1.onCompleted();
                        }
                    }

                });
            }
        };

        LoadBalancerCommand<String> command = LoadBalancerCommand.<String>builder()
                .withLoadBalancer(loadBalancer)
                .withRetryHandler(handler)
                .build();

        String result = command.submit(operation).toBlocking().single();
        assertEquals("3", result); // server2 is picked first
        assertEquals(1, loadBalancer.getLoadBalancerStats().getSingleServerStat(server3).getTotalRequestsCount());
    }
}
