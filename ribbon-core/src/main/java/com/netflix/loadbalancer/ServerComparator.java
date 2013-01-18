package com.netflix.loadbalancer;

import java.io.Serializable;
import java.util.Comparator;

public class ServerComparator implements Comparator<Server>, Serializable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public int compare(Server s1, Server s2) {
        return s1.getHostPort().compareTo(s2.getId());
    }
}
