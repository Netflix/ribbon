/*
 *
 * Copyright 2014 Netflix, Inc.
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
 *
 */
package com.netflix.loadbalancer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class ServerListChangeListenerTest {

    private volatile List<Server> oldList;
    private volatile List<Server> newList;
    
    @Test
    public void testListener() {
        BaseLoadBalancer lb = new BaseLoadBalancer();
        lb.addServerListChangeListener(new ServerListChangeListener() {
            @Override
            public void serverListChanged(List<Server> oldList, List<Server> newList) {
                ServerListChangeListenerTest.this.oldList = oldList;
                ServerListChangeListenerTest.this.newList = newList;
            }
        });
        List<Server> list1 = Lists.newArrayList(new Server("server1"), new Server("server2"));
        List<Server> list2 = Lists.newArrayList(new Server("server3"), new Server("server4"));
        lb.setServersList(list1);
        assertTrue(oldList.isEmpty());
        assertEquals(list1, newList);
        lb.setServersList(list2);
        assertEquals(list1, oldList);
        assertEquals(list2, newList);
    }
}
