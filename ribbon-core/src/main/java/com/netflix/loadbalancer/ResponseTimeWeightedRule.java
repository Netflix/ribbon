package com.netflix.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* 
 * Rule that use the average/percentile response times
 * to assign dynamic "weights" per Server which is then used in 
 * the "Weighted Round Robin" fashion
 * 
 * The basic idea for weighted round robin has been obtained from JCS
 * The implementation for choosing the endpoint from the list of endpoints
 * is as follows:Let's assume 4 endpoints:A(wt=10), B(wt=30), C(wt=40), 
 * D(wt=20). 
 * Using the Random API, generate a random number between 1 and10+30+40+20.
 * Let's assume that the above list is randomized. Based on the weights, we
 * have intervals as follows:
 * 1-----10 (A's weight)
 * 11----40 (A's weight + B's weight)
 * 41----80 (A's weight + B's weight + C's weight)
 * 81----100(A's weight + B's weight + C's weight + C's weight)
 * Here's the psuedo code for deciding where to send the request:
 * if (random_number between 1 & 10) {send request to A;}
 * else if (random_number between 11 & 40) {send request to B;}
 * else if (random_number between 41 & 80) {send request to C;}
 * else if (random_number between 81 & 100) {send request to D;}
 * 
 * @author Sudhir Tonse (stonse@netflix.com)
 */
public class ResponseTimeWeightedRule implements IRule {

    private static final int serverWeightTaskTimerInterval = 30 * 1000;

    private static final Logger logger = LoggerFactory.getLogger(ResponseTimeWeightedRule.class);

    ILoadBalancer lb = null;

    Map<Server, Double> serverWeights = new ConcurrentHashMap<Server, Double>();

    List<Double> finalWeights = new ArrayList<Double>();

    double maxTotalWeight = 0.0;

    private final Random random = new Random(System.currentTimeMillis());

    protected Timer serverWeightTimer = null;

    protected AtomicBoolean serverWeightAssignmentInProgress = new AtomicBoolean(
            false);

    String name = "unknown";

    public ResponseTimeWeightedRule() {

    }

    public ILoadBalancer getLoadBalancer() {
        return lb;
    }

    public void setLoadBalancer(ILoadBalancer lb) {
        this.lb = lb;
        if (lb instanceof BaseLoadBalancer) {
            name = ((BaseLoadBalancer) lb).getName();
        }
    }

    public void initialize(ILoadBalancer lb) {
        setLoadBalancer(lb);
        if (serverWeightTimer != null) {
            serverWeightTimer.cancel();
        }
        serverWeightTimer = new Timer("NFLoadBalancer-serverWeightTimer-"
                + name, true);
        serverWeightTimer.schedule(new DynamicServerWeightTask(), 0,
                serverWeightTaskTimerInterval);
        // do a initialrun
        ServerWeight sw = new ServerWeight();
        sw.maintainWeights();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                logger
                        .info("Stopping NFLoadBalancer-serverWeightTimer-"
                                + name);
                serverWeightTimer.cancel();
            }
        }));
    }

    public void shutdown() {
        if (serverWeightTimer != null) {
            logger.info("Stopping NFLoadBalancer-serverWeightTimer-" + name);
            serverWeightTimer.cancel();
        }
    }

    final static boolean availableOnly = false;

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE")
    public Server choose(BaseLoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        }
        Server server = null;

        while (server == null) {
            if (Thread.interrupted()) {
                return null;
            }

            int upCount = lb.getServerCount(true);
            int serverCount = lb.getServerCount(availableOnly);

            if ((upCount == 0) || (serverCount == 0)) {
                return null;
            }

            double randomIndex = 0;

            while (randomIndex == 0) {
                randomIndex = random.nextDouble() * maxTotalWeight;
                if (randomIndex != 0) {
                    break;
                }
            }
            int serverIndex = 0;

            // pick the server index based on the randomIndex
            int n = 0;
            for (Double d : finalWeights) {
                if (randomIndex <= d) {
                    serverIndex = n;
                } else {
                    n++;
                }
            }

            server = lb.getServerByIndex(serverIndex, availableOnly);

            if (server == null) {
                /* Transient. */
                Thread.yield();
                continue;
            }

            if (server.isAlive()) {
                return (server);
            }

            // Next.
            server = null;
        }
        return server;
    }

    class DynamicServerWeightTask extends TimerTask {
        public void run() {
            ServerWeight serverWeight = new ServerWeight();
            try {
                serverWeight.maintainWeights();
            } catch (Throwable t) {
                String lbName = "unknown";
                BaseLoadBalancer nlb = (BaseLoadBalancer) lb;
                lbName = nlb.getName();
                logger.error(
                        "Throwable caught while running DynamicServerWeightTask for "
                                + lbName, t);
            }
        }
    }

    class ServerWeight {

        public void maintainWeights() {
            if (lb == null) {
                return;
            }
            if (serverWeightAssignmentInProgress.get()) {
                return; // Ping in progress - nothing to do
            } else {
                serverWeightAssignmentInProgress.set(true);
            }

            try {

                BaseLoadBalancer nlb = (BaseLoadBalancer) lb;
                for (Server server : nlb.getServerList(availableOnly)) {
                    Double weight = 10.00;
                    if (nlb.getLoadBalancerStats() != null) {
                        if (nlb.getLoadBalancerStats().getServerStats().get(
                                server) != null) {
                            ServerStats ss = nlb.getLoadBalancerStats()
                                    .getServerStats().get(server);
                            weight = ss.getResponseTime95thPercentile();
                        } else {
                            nlb.getLoadBalancerStats().addServer(server);
                        }
                    }
                    serverWeights.put(server, weight);
                }
                // calculate final weights
                Double weightSoFar = 0.0;
                finalWeights.clear();
                for (Server server : nlb.getServerList(availableOnly)) {
                    weightSoFar += serverWeights.get(server);
                    finalWeights.add(weightSoFar);
                }
                maxTotalWeight = weightSoFar;
            } catch (Throwable t) {
                logger
                        .error(
                                "Exception while dynamically calculating server weights",
                                t);
            } finally {
                serverWeightAssignmentInProgress.set(false);
            }

        }
    }

}
