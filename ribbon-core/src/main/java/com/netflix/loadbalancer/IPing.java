package com.netflix.loadbalancer;

/**
 * Interface that defines how we "ping" a server to check if its alive
 * @author stonse
 *
 */
public interface IPing {
    /*
     *
     * Send one ping only.
     *
     * Well, send what you need to determine whether or not the
     * server is still alive.  Should be interruptible.  Will
     * run in a separate thread.
	 *
	 * Warning:  multiple threads will execute this method 
	 * simultaneously.  Must be MT-safe.
     */

    public boolean isAlive(Server server);
}
