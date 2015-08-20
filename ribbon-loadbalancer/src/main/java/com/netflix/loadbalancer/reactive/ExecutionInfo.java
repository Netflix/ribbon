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
package com.netflix.loadbalancer.reactive;

import com.netflix.loadbalancer.Server;

/**
 * Represents the state of execution for an instance of {@link com.netflix.loadbalancer.reactive.LoadBalancerCommand}
 * and is passed to {@link ExecutionListener}
 *
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
