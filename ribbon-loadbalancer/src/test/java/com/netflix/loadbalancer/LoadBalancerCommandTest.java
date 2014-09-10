package com.netflix.loadbalancer;

import com.google.common.collect.Lists;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.reactive.CommandBuilder;
import com.netflix.loadbalancer.reactive.LoadBalancerObservable;
import com.netflix.loadbalancer.reactive.LoadBalancerObservableCommand;
import com.netflix.loadbalancer.reactive.LoadBalancerRetrySameServerCommand;
import org.junit.Test;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

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

        LoadBalancerRetrySameServerCommand<String> command = CommandBuilder.<String>newBuilder().withLoadBalancer(loadBalancer)
                .withRetryHandler(handler)
                .build();
        LoadBalancerObservable<String> observableProvider = new LoadBalancerObservable<String>() {
            AtomicInteger count = new AtomicInteger();
            @Override
            public Observable<String> run(final Server server) {
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
        String result = command.retryWithSameServer(server1, observableProvider.run(server1)).toBlocking().single();
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
        LoadBalancerObservable<String> observableProvider = new LoadBalancerObservable<String>() {
            AtomicInteger count = new AtomicInteger();
            @Override
            public Observable<String> run(final Server server) {
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

        LoadBalancerObservableCommand<String> command = CommandBuilder.<String>newBuilder().withLoadBalancer(loadBalancer)
                .withRetryHandler(handler)
                .build(observableProvider);

        String result = command.toObservable().toBlocking().single();
        assertEquals("3", result); // server2 is picked first
        assertEquals(2, loadBalancer.getLoadBalancerStats().getSingleServerStat(server2).getTotalRequestsCount());
        assertEquals(1, loadBalancer.getLoadBalancerStats().getSingleServerStat(server3).getTotalRequestsCount());
    }
}
