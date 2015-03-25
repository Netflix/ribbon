package com.netflix.loadbalancer;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertThat;

public class ServerStatusChangeListenerTest {

    private final Server server1 = new Server("www.google.com:80");
    private final Server server2 = new Server("www.netflix.com:80");

    private BaseLoadBalancer lb;
    private AtomicReference<List<Server>> serversReceivedByListener;

    @Before
    public void setupLoadbalancerAndListener() {
        lb = new BaseLoadBalancer();
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
    public void changeServerStatusByPingShouldBeReceivedByListener() {
        final PingConstant ping = new PingConstant();
        lb.setPing(ping);

        ping.setConstant(false);
        lb.forceQuickPing();
        assertThat(serversReceivedByListener.get(), allOf(hasItem(server1), hasItem(server2)));

        ping.setConstant(true);
        lb.forceQuickPing();
        assertThat(serversReceivedByListener.get(), allOf(hasItem(server1), hasItem(server2)));
    }

}
