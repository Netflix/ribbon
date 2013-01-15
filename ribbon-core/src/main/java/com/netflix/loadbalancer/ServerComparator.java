package com.netflix.loadbalancer;

import java.util.Comparator;

public class ServerComparator implements Comparator<Server> {
    public int compare(Server s1, Server s2) {
        return s1.getHostPort().compareTo(s2.getId());
    }
}
