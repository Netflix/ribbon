/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.loadbalancer;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

public class ServerStatusChangeListenerTest {

    /**
     * A load balancer that has all the functionality of the Base one except it does not
     * schedule any ping tasks that can concurrently update the state of the LB by either
     * causing forced pings to be dropped or changing the expected state of the test before
     * we assert our invariants.
     */
    private class NoPingTaskLoadBalancer extends BaseLoadBalancer {
        @Override
        void setupPingTask() {}
    }

    private final Server server1 = new Server("server1");
    private final Server server2 = new Server("server2");
    
    private BaseLoadBalancer lb;
    private AtomicReference<List<Server>> serversReceivedByListener;

    @Before
    public void setupLoadbalancerAndListener() {
        lb = new NoPingTaskLoadBalancer();
        lb.setServersList(asList(server1, server2));
        serversReceivedByListener = new AtomicReference<List<Server>>();
        lb.addServerStatusChangeListener(new ServerStatusChangeListener() {
            @Override
            public void serverStatusChanged(final Collection<Server> servers) {
                serversReceivedByListener.set(new ArrayList<Server>(servers));
            }
        });
    }

    @Test
    public void markServerDownByIdShouldBeReceivedByListener() {
        lb.markServerDown(server1.getId());
        assertThat(serversReceivedByListener.get(), is(singletonList(server1)));
        lb.markServerDown(server2.getId());
        assertThat(serversReceivedByListener.get(), is(singletonList(server2)));
    }

    @Test
    public void markServerDownByObjectShouldBeReceivedByListener() {
        lb.markServerDown(server1);
        assertThat(serversReceivedByListener.get(), is(singletonList(server1)));
        lb.markServerDown(server2);
        assertThat(serversReceivedByListener.get(), is(singletonList(server2)));
    }

    @Test
    public void changeServerStatusByPingShouldBeReceivedByListener() throws InterruptedException {
        final PingConstant ping = new PingConstant();
        // Start with a ping where both servers are down.
        ping.setConstant(false);
        lb.setPing(ping);
        lb.forceQuickPing();
        // We should see that the servers that changed status are both server1 and 2.
        assertThat(serversReceivedByListener.get(), allOf(hasItem(server1), hasItem(server2)));

        // Bring both servers back up.
        ping.setConstant(true);
        // Clear the list so we can see the change-list is non-empty after the listener is called.
        serversReceivedByListener.set(null);
        lb.forceQuickPing();
        assertFalse(serversReceivedByListener.get().isEmpty());
        assertThat(serversReceivedByListener.get(), allOf(hasItem(server1), hasItem(server2)));
    }

}
