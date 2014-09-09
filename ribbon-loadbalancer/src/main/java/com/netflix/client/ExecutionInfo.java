package com.netflix.client;

import com.netflix.loadbalancer.Server;

/**
 * @author Allen Wang
 */
public class ExecutionInfo {

    private final Server server;
    private final int numberOfPastAttemptsOnServer;
    private final int numberOfPastServersAttempted;

    private ExecutionInfo(Server server, int numberOfPastAttemptsOnServer, int numberOfPastServersAttempted) {
        this.server = server;
        this.numberOfPastAttemptsOnServer = numberOfPastAttemptsOnServer;
        this.numberOfPastServersAttempted = numberOfPastServersAttempted;
    }

    public static ExecutionInfo create(Server server, int numberOfPastAttemptsOnServer, int numberOfPastServersAttempted) {
        return new ExecutionInfo(server, numberOfPastAttemptsOnServer, numberOfPastServersAttempted);
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

    @Override
    public String toString() {
        return "ExecutionInfo{" +
                "server=" + server +
                ", numberOfPastAttemptsOnServer=" + numberOfPastAttemptsOnServer +
                ", numberOfPastServersAttempted=" + numberOfPastServersAttempted +
                '}';
    }
}
