package com.netflix.client;

import com.netflix.loadbalancer.Server;

/**
 * @author Allen Wang
 */
public class ExecutionInfo {

    private final Server server;
    private final int numberOfPastAttemptsOnServer;
    private final int numberOfPastServersAttempted;

    public ExecutionInfo(Server server, int numberOfPastAttemptsOnServer, int numberOfPastServersAttempted) {
        this.server = server;
        this.numberOfPastAttemptsOnServer = numberOfPastAttemptsOnServer;
        this.numberOfPastServersAttempted = numberOfPastServersAttempted;
    }

    public Server getServer() {
        return server;
    }

    public int getNumberOfPastAttemptsOnServer() {
        return numberOfPastAttemptsOnServer;
    }

    public int getNumberOfPastServersAttempted() {
        return numberOfPastServersAttempted;
    }
}
