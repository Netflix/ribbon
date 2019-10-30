/*
 *
 * Copyright 2013 Netflix, Inc.
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

import static java.util.Collections.singleton;

import com.google.common.collect.ImmutableList;
import com.netflix.client.ClientFactory;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.PrimeConnections;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.util.concurrent.ShutdownEnabledTimer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A basic implementation of the load balancer where an arbitrary list of
 * servers can be set as the server pool. A ping can be set to determine the
 * liveness of a server. Internally, this class maintains an "all" server list
 * and an "up" server list and use them depending on what the caller asks for.
 * 
 * @author stonse
 * 
 */
public class BaseLoadBalancer extends AbstractLoadBalancer implements
        PrimeConnections.PrimeConnectionListener, IClientConfigAware {

    private static Logger logger = LoggerFactory.getLogger(BaseLoadBalancer.class);

    private final static IRule DEFAULT_RULE = new RoundRobinRule();
    private final static SerialPingStrategy DEFAULT_PING_STRATEGY = new SerialPingStrategy();
    private static final String DEFAULT_NAME = "default";
    private static final String PREFIX = "LoadBalancer_";

    protected IRule rule = DEFAULT_RULE;

    protected IPingStrategy pingStrategy = DEFAULT_PING_STRATEGY;

    protected IPing ping = null;

    @Monitor(name = PREFIX + "AllServerList", type = DataSourceType.INFORMATIONAL)
    protected volatile List<Server> allServerList = Collections
            .synchronizedList(new ArrayList<Server>());
    @Monitor(name = PREFIX + "UpServerList", type = DataSourceType.INFORMATIONAL)
    protected volatile List<Server> upServerList = Collections
            .synchronizedList(new ArrayList<Server>());

    protected ReadWriteLock allServerLock = new ReentrantReadWriteLock();
    protected ReadWriteLock upServerLock = new ReentrantReadWriteLock();

    protected String name = DEFAULT_NAME;

    protected Timer lbTimer = null;
    protected int pingIntervalSeconds = 10;
    protected int maxTotalPingTimeSeconds = 5;
    protected Comparator<Server> serverComparator = new ServerComparator();

    protected AtomicBoolean pingInProgress = new AtomicBoolean(false);

    protected LoadBalancerStats lbStats;

    private volatile Counter counter = Monitors.newCounter("LoadBalancer_ChooseServer");

    private PrimeConnections primeConnections;

    private volatile boolean enablePrimingConnections = false;
    
    private IClientConfig config;
    
    private List<ServerListChangeListener> changeListeners = new CopyOnWriteArrayList<ServerListChangeListener>();

    private List<ServerStatusChangeListener> serverStatusListeners = new CopyOnWriteArrayList<ServerStatusChangeListener>();

    /**
     * Default constructor which sets name as "default", sets null ping, and
     * {@link RoundRobinRule} as the rule.
     * <p>
     * This constructor is mainly used by {@link ClientFactory}. Calling this
     * constructor must be followed by calling {@link #init()} or
     * {@link #initWithNiwsConfig(IClientConfig)} to complete initialization.
     * This constructor is provided for reflection. When constructing
     * programatically, it is recommended to use other constructors.
     */
    public BaseLoadBalancer() {
        this.name = DEFAULT_NAME;
        this.ping = null;
        setRule(DEFAULT_RULE);
        setupPingTask();
        lbStats = new LoadBalancerStats(DEFAULT_NAME);
    }

    public BaseLoadBalancer(String lbName, IRule rule, LoadBalancerStats lbStats) {
        this(lbName, rule, lbStats, null);
    }

    public BaseLoadBalancer(IPing ping, IRule rule) {
        this(DEFAULT_NAME, rule, new LoadBalancerStats(DEFAULT_NAME), ping);
    }

    public BaseLoadBalancer(IPing ping, IRule rule, IPingStrategy pingStrategy) {
        this(DEFAULT_NAME, rule, new LoadBalancerStats(DEFAULT_NAME), ping, pingStrategy);
    }

    public BaseLoadBalancer(String name, IRule rule, LoadBalancerStats stats,
            IPing ping) {
        this(name, rule, stats, ping, DEFAULT_PING_STRATEGY);
    }
    
    public BaseLoadBalancer(String name, IRule rule, LoadBalancerStats stats,
            IPing ping, IPingStrategy pingStrategy) {
	
        logger.debug("LoadBalancer [{}]:  initialized", name);
        
        this.name = name;
        this.ping = ping;
        this.pingStrategy = pingStrategy;
        setRule(rule);
        setupPingTask();
        lbStats = stats;
        init();
    }

    public BaseLoadBalancer(IClientConfig config) {
        initWithNiwsConfig(config);
    }

    public BaseLoadBalancer(IClientConfig config, IRule rule, IPing ping) {
        initWithConfig(config, rule, ping, createLoadBalancerStatsFromConfig(config, ClientFactory::instantiateInstanceWithClientConfig));
    }

    void initWithConfig(IClientConfig clientConfig, IRule rule, IPing ping) {
        initWithConfig(clientConfig, rule, ping, createLoadBalancerStatsFromConfig(config, ClientFactory::instantiateInstanceWithClientConfig));
    }
    
    void initWithConfig(IClientConfig clientConfig, IRule rule, IPing ping, LoadBalancerStats stats) {
        this.config = clientConfig;
        this.name = clientConfig.getClientName();
        int pingIntervalTime = clientConfig.get(CommonClientConfigKey.NFLoadBalancerPingInterval, 30);
        int maxTotalPingTime = clientConfig.get(CommonClientConfigKey.NFLoadBalancerMaxTotalPingTime, 2);

        setPingInterval(pingIntervalTime);
        setMaxTotalPingTime(maxTotalPingTime);

        // cross associate with each other
        // i.e. Rule,Ping meet your container LB
        // LB, these are your Ping and Rule guys ...
        setRule(rule);
        setPing(ping);

        setLoadBalancerStats(stats);
        rule.setLoadBalancer(this);
        if (ping instanceof AbstractLoadBalancerPing) {
            ((AbstractLoadBalancerPing) ping).setLoadBalancer(this);
        }
        logger.info("Client: {} instantiated a LoadBalancer: {}", name, this);
        boolean enablePrimeConnections = clientConfig.getOrDefault(CommonClientConfigKey.EnablePrimeConnections);

        if (enablePrimeConnections) {
            this.setEnablePrimingConnections(true);
            PrimeConnections primeConnections = new PrimeConnections(
                    this.getName(), clientConfig);
            this.setPrimeConnections(primeConnections);
        }
        init();

    }
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        try {
            initWithNiwsConfig(clientConfig, ClientFactory::instantiateInstanceWithClientConfig);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing load balancer", e);
        }
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig, Factory factory) {
        String ruleClassName = clientConfig.getOrDefault(CommonClientConfigKey.NFLoadBalancerRuleClassName);
        String pingClassName = clientConfig.getOrDefault(CommonClientConfigKey.NFLoadBalancerPingClassName);
        try {
            IRule rule = (IRule)factory.create(ruleClassName, clientConfig);
            IPing ping = (IPing)factory.create(pingClassName, clientConfig);
            LoadBalancerStats stats = createLoadBalancerStatsFromConfig(clientConfig, factory);
            initWithConfig(clientConfig, rule, ping, stats);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing load balancer", e);
        }
    }

    private LoadBalancerStats createLoadBalancerStatsFromConfig(IClientConfig clientConfig, Factory factory) {
        String loadBalancerStatsClassName = clientConfig.getOrDefault(CommonClientConfigKey.NFLoadBalancerStatsClassName);
        try {
            return (LoadBalancerStats) factory.create(loadBalancerStatsClassName, clientConfig);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error initializing configured LoadBalancerStats class - " + loadBalancerStatsClassName,
                    e);
        }
    }

    public void addServerListChangeListener(ServerListChangeListener listener) {
        changeListeners.add(listener);
    }
    
    public void removeServerListChangeListener(ServerListChangeListener listener) {
        changeListeners.remove(listener);
    }

    public void addServerStatusChangeListener(ServerStatusChangeListener listener) {
        serverStatusListeners.add(listener);
    }

    public void removeServerStatusChangeListener(ServerStatusChangeListener listener) {
        serverStatusListeners.remove(listener);
    }

    public IClientConfig getClientConfig() {
    	return config;
    }
    
    private boolean canSkipPing() {
        if (ping == null
                || ping.getClass().getName().equals(DummyPing.class.getName())) {
            // default ping, no need to set up timer
            return true;
        } else {
            return false;
        }
    }

    void setupPingTask() {
        if (canSkipPing()) {
            return;
        }
        if (lbTimer != null) {
            lbTimer.cancel();
        }
        lbTimer = new ShutdownEnabledTimer("NFLoadBalancer-PingTimer-" + name,
                true);
        lbTimer.schedule(new PingTask(), 0, pingIntervalSeconds * 1000);
        forceQuickPing();
    }

    /**
     * Set the name for the load balancer. This should not be called since name
     * should be immutable after initialization. Calling this method does not
     * guarantee that all other data structures that depend on this name will be
     * changed accordingly.
     */
    void setName(String name) {
        // and register
        this.name = name;
        if (lbStats == null) {
            lbStats = new LoadBalancerStats(name);
        } else {
            lbStats.setName(name);
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public LoadBalancerStats getLoadBalancerStats() {
        return lbStats;
    }

    public void setLoadBalancerStats(LoadBalancerStats lbStats) {
        this.lbStats = lbStats;
    }

    public Lock lockAllServerList(boolean write) {
        Lock aproposLock = write ? allServerLock.writeLock() : allServerLock
                .readLock();
        aproposLock.lock();
        return aproposLock;
    }

    public Lock lockUpServerList(boolean write) {
        Lock aproposLock = write ? upServerLock.writeLock() : upServerLock
                .readLock();
        aproposLock.lock();
        return aproposLock;
    }

    public void setPingInterval(int pingIntervalSeconds) {
        if (pingIntervalSeconds < 1) {
            return;
        }

        this.pingIntervalSeconds = pingIntervalSeconds;
        if (logger.isDebugEnabled()) {
            logger.debug("LoadBalancer [{}]:  pingIntervalSeconds set to {}",
        	    name, this.pingIntervalSeconds);
        }
        setupPingTask(); // since ping data changed
    }

    public int getPingInterval() {
        return pingIntervalSeconds;
    }

    /*
     * Maximum time allowed for the ping cycle
     */
    public void setMaxTotalPingTime(int maxTotalPingTimeSeconds) {
        if (maxTotalPingTimeSeconds < 1) {
            return;
        }
        this.maxTotalPingTimeSeconds = maxTotalPingTimeSeconds;
        logger.debug("LoadBalancer [{}]: maxTotalPingTime set to {}", name, this.maxTotalPingTimeSeconds);

    }

    public int getMaxTotalPingTime() {
        return maxTotalPingTimeSeconds;
    }

    public IPing getPing() {
        return ping;
    }

    public IRule getRule() {
        return rule;
    }

    public boolean isPingInProgress() {
        return pingInProgress.get();
    }

    /* Specify the object which is used to send pings. */

    public void setPing(IPing ping) {
        if (ping != null) {
            if (!ping.equals(this.ping)) {
                this.ping = ping;
                setupPingTask(); // since ping data changed
            }
        } else {
            this.ping = null;
            // cancel the timer task
            lbTimer.cancel();
        }
    }

    /* Ignore null rules */

    public void setRule(IRule rule) {
        if (rule != null) {
            this.rule = rule;
        } else {
            /* default rule */
            this.rule = new RoundRobinRule();
        }
        if (this.rule.getLoadBalancer() != this) {
            this.rule.setLoadBalancer(this);
        }
    }

    /**
     * get the count of servers.
     * 
     * @param onlyAvailable
     *            if true, return only up servers.
     */
    public int getServerCount(boolean onlyAvailable) {
        if (onlyAvailable) {
            return upServerList.size();
        } else {
            return allServerList.size();
        }
    }

    /**
     * Add a server to the 'allServer' list; does not verify uniqueness, so you
     * could give a server a greater share by adding it more than once.
     */
    public void addServer(Server newServer) {
        if (newServer != null) {
            try {
                ArrayList<Server> newList = new ArrayList<Server>();

                newList.addAll(allServerList);
                newList.add(newServer);
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Error adding newServer {}", name, newServer.getHost(), e);
            }
        }
    }

    /**
     * Add a list of servers to the 'allServer' list; does not verify
     * uniqueness, so you could give a server a greater share by adding it more
     * than once
     */
    @Override
    public void addServers(List<Server> newServers) {
        if (newServers != null && newServers.size() > 0) {
            try {
                ArrayList<Server> newList = new ArrayList<Server>();
                newList.addAll(allServerList);
                newList.addAll(newServers);
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Exception while adding Servers", name, e);
            }
        }
    }

    /*
     * Add a list of servers to the 'allServer' list; does not verify
     * uniqueness, so you could give a server a greater share by adding it more
     * than once USED by Test Cases only for legacy reason. DO NOT USE!!
     */
    void addServers(Object[] newServers) {
        if ((newServers != null) && (newServers.length > 0)) {

            try {
                ArrayList<Server> newList = new ArrayList<Server>();
                newList.addAll(allServerList);

                for (Object server : newServers) {
                    if (server != null) {
                        if (server instanceof String) {
                            server = new Server((String) server);
                        }
                        if (server instanceof Server) {
                            newList.add((Server) server);
                        }
                    }
                }
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Exception while adding Servers", name, e);
            }
        }
    }

    /**
     * Set the list of servers used as the server pool. This overrides existing
     * server list.
     */
    public void setServersList(List lsrv) {
        Lock writeLock = allServerLock.writeLock();
        logger.debug("LoadBalancer [{}]: clearing server list (SET op)", name);
        
        ArrayList<Server> newServers = new ArrayList<Server>();
        writeLock.lock();
        try {
            ArrayList<Server> allServers = new ArrayList<Server>();
            for (Object server : lsrv) {
                if (server == null) {
                    continue;
                }

                if (server instanceof String) {
                    server = new Server((String) server);
                }

                if (server instanceof Server) {
                    logger.debug("LoadBalancer [{}]:  addServer [{}]", name, ((Server) server).getId());
                    allServers.add((Server) server);
                } else {
                    throw new IllegalArgumentException(
                            "Type String or Server expected, instead found:"
                                    + server.getClass());
                }

            }
            boolean listChanged = false;
            if (!allServerList.equals(allServers)) {
                listChanged = true;
                if (changeListeners != null && changeListeners.size() > 0) {
                   List<Server> oldList = ImmutableList.copyOf(allServerList);
                   List<Server> newList = ImmutableList.copyOf(allServers);                   
                   for (ServerListChangeListener l: changeListeners) {
                       try {
                           l.serverListChanged(oldList, newList);
                       } catch (Exception e) {
                           logger.error("LoadBalancer [{}]: Error invoking server list change listener", name, e);
                       }
                   }
                }
            }
            if (isEnablePrimingConnections()) {
                for (Server server : allServers) {
                    if (!allServerList.contains(server)) {
                        server.setReadyToServe(false);
                        newServers.add((Server) server);
                    }
                }
                if (primeConnections != null) {
                    primeConnections.primeConnectionsAsync(newServers, this);
                }
            }
            // This will reset readyToServe flag to true on all servers
            // regardless whether
            // previous priming connections are success or not
            allServerList = allServers;
            if (canSkipPing()) {
                for (Server s : allServerList) {
                    s.setAlive(true);
                }
                upServerList = allServerList;
            } else if (listChanged) {
                forceQuickPing();
            }
        } finally {
            writeLock.unlock();
        }
    }

    /* List in string form. SETS, does not add. */
    void setServers(String srvString) {
        if (srvString != null) {

            try {
                String[] serverArr = srvString.split(",");
                ArrayList<Server> newList = new ArrayList<Server>();

                for (String serverString : serverArr) {
                    if (serverString != null) {
                        serverString = serverString.trim();
                        if (serverString.length() > 0) {
                            Server svr = new Server(serverString);
                            newList.add(svr);
                        }
                    }
                }
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Exception while adding Servers", name, e);
            }
        }
    }

    /**
     * return the server
     * 
     * @param index
     * @param availableOnly
     */
    public Server getServerByIndex(int index, boolean availableOnly) {
        try {
            return (availableOnly ? upServerList.get(index) : allServerList
                    .get(index));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Server> getServerList(boolean availableOnly) {
        return (availableOnly ? getReachableServers() : getAllServers());
    }

    @Override
    public List<Server> getReachableServers() {
        return Collections.unmodifiableList(upServerList);
    }

    @Override
    public List<Server> getAllServers() {
        return Collections.unmodifiableList(allServerList);
    }

    @Override
    public List<Server> getServerList(ServerGroup serverGroup) {
        switch (serverGroup) {
        case ALL:
            return allServerList;
        case STATUS_UP:
            return upServerList;
        case STATUS_NOT_UP:
            ArrayList<Server> notAvailableServers = new ArrayList<Server>(
                    allServerList);
            ArrayList<Server> upServers = new ArrayList<Server>(upServerList);
            notAvailableServers.removeAll(upServers);
            return notAvailableServers;
        }
        return new ArrayList<Server>();
    }

    public void cancelPingTask() {
        if (lbTimer != null) {
            lbTimer.cancel();
        }
    }

    /**
     * TimerTask that keeps runs every X seconds to check the status of each
     * server/node in the Server List
     * 
     * @author stonse
     * 
     */
    class PingTask extends TimerTask {
        public void run() {
            try {
            	new Pinger(pingStrategy).runPinger();
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Error pinging", name, e);
            }
        }
    }

    /**
     * Class that contains the mechanism to "ping" all the instances
     * 
     * @author stonse
     *
     */
    class Pinger {

        private final IPingStrategy pingerStrategy;

        public Pinger(IPingStrategy pingerStrategy) {
            this.pingerStrategy = pingerStrategy;
        }

        public void runPinger() throws Exception {
            if (!pingInProgress.compareAndSet(false, true)) { 
                return; // Ping in progress - nothing to do
            }
            
            // we are "in" - we get to Ping

            Server[] allServers = null;
            boolean[] results = null;

            Lock allLock = null;
            Lock upLock = null;

            try {
                /*
                 * The readLock should be free unless an addServer operation is
                 * going on...
                 */
                allLock = allServerLock.readLock();
                allLock.lock();
                allServers = allServerList.toArray(new Server[allServerList.size()]);
                allLock.unlock();

                int numCandidates = allServers.length;
                results = pingerStrategy.pingServers(ping, allServers);

                final List<Server> newUpList = new ArrayList<Server>();
                final List<Server> changedServers = new ArrayList<Server>();

                for (int i = 0; i < numCandidates; i++) {
                    boolean isAlive = results[i];
                    Server svr = allServers[i];
                    boolean oldIsAlive = svr.isAlive();

                    svr.setAlive(isAlive);

                    if (oldIsAlive != isAlive) {
                        changedServers.add(svr);
                        logger.debug("LoadBalancer [{}]:  Server [{}] status changed to {}", 
                    		name, svr.getId(), (isAlive ? "ALIVE" : "DEAD"));
                    }

                    if (isAlive) {
                        newUpList.add(svr);
                    }
                }
                upLock = upServerLock.writeLock();
                upLock.lock();
                upServerList = newUpList;
                upLock.unlock();

                notifyServerStatusChangeListener(changedServers);
            } finally {
                pingInProgress.set(false);
            }
        }
    }

    private void notifyServerStatusChangeListener(final Collection<Server> changedServers) {
        if (changedServers != null && !changedServers.isEmpty() && !serverStatusListeners.isEmpty()) {
            for (ServerStatusChangeListener listener : serverStatusListeners) {
                try {
                    listener.serverStatusChanged(changedServers);
                } catch (Exception e) {
                    logger.error("LoadBalancer [{}]: Error invoking server status change listener", name, e);
                }
            }
        }
    }

    private final Counter createCounter() {
        return Monitors.newCounter("LoadBalancer_ChooseServer");
    }

    /*
     * Get the alive server dedicated to key
     * 
     * @return the dedicated server
     */
    public Server chooseServer(Object key) {
        if (counter == null) {
            counter = createCounter();
        }
        counter.increment();
        if (rule == null) {
            return null;
        } else {
            try {
                return rule.choose(key);
            } catch (Exception e) {
                logger.warn("LoadBalancer [{}]:  Error choosing server for key {}", name, key, e);
                return null;
            }
        }
    }

    /* Returns either null, or "server:port/servlet" */
    public String choose(Object key) {
        if (rule == null) {
            return null;
        } else {
            try {
                Server svr = rule.choose(key);
                return ((svr == null) ? null : svr.getId());
            } catch (Exception e) {
                logger.warn("LoadBalancer [{}]:  Error choosing server", name, e);
                return null;
            }
        }
    }

    public void markServerDown(Server server) {
        if (server == null || !server.isAlive()) {
            return;
        }

        logger.error("LoadBalancer [{}]:  markServerDown called on [{}]", name, server.getId());
        server.setAlive(false);
        // forceQuickPing();

        notifyServerStatusChangeListener(singleton(server));
    }

    public void markServerDown(String id) {
        boolean triggered = false;

        id = Server.normalizeId(id);

        if (id == null) {
            return;
        }

        Lock writeLock = upServerLock.writeLock();
    	writeLock.lock();
        try {
            final List<Server> changedServers = new ArrayList<Server>();

            for (Server svr : upServerList) {
                if (svr.isAlive() && (svr.getId().equals(id))) {
                    triggered = true;
                    svr.setAlive(false);
                    changedServers.add(svr);
                }
            }

            if (triggered) {
                logger.error("LoadBalancer [{}]:  markServerDown called for server [{}]", name, id);
                notifyServerStatusChangeListener(changedServers);
            }

        } finally {
            writeLock.unlock();
        }
    }

    /*
     * Force an immediate ping, if we're not currently pinging and don't have a
     * quick-ping already scheduled.
     */
    public void forceQuickPing() {
        if (canSkipPing()) {
            return;
        }
        logger.debug("LoadBalancer [{}]:  forceQuickPing invoking", name);
        
        try {
        	new Pinger(pingStrategy).runPinger();
        } catch (Exception e) {
            logger.error("LoadBalancer [{}]: Error running forceQuickPing()", name, e);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{NFLoadBalancer:name=").append(this.getName())
                .append(",current list of Servers=").append(this.allServerList)
                .append(",Load balancer stats=")
                .append(this.lbStats.toString()).append("}");
        return sb.toString();
    }

    /**
     * Register with monitors and start priming connections if it is set.
     */
    protected void init() {
        Monitors.registerObject("LoadBalancer_" + name, this);
        // register the rule as it contains metric for available servers count
        Monitors.registerObject("Rule_" + name, this.getRule());
        if (enablePrimingConnections && primeConnections != null) {
            primeConnections.primeConnections(getReachableServers());
        }
    }

    public final PrimeConnections getPrimeConnections() {
        return primeConnections;
    }

    public final void setPrimeConnections(PrimeConnections primeConnections) {
        this.primeConnections = primeConnections;
    }

    @Override
    public void primeCompleted(Server s, Throwable lastException) {
        s.setReadyToServe(true);
    }

    public boolean isEnablePrimingConnections() {
        return enablePrimingConnections;
    }

    public final void setEnablePrimingConnections(
            boolean enablePrimingConnections) {
        this.enablePrimingConnections = enablePrimingConnections;
    }
    
    public void shutdown() {
        cancelPingTask();
        if (primeConnections != null) {
            primeConnections.shutdown();
        }
        Monitors.unregisterObject("LoadBalancer_" + name, this);
        Monitors.unregisterObject("Rule_" + name, this.getRule());
    }

    /**
     * Default implementation for <c>IPingStrategy</c>, performs ping
     * serially, which may not be desirable, if your <c>IPing</c>
     * implementation is slow, or you have large number of servers.
     */
    private static class SerialPingStrategy implements IPingStrategy {

        @Override
        public boolean[] pingServers(IPing ping, Server[] servers) {
            int numCandidates = servers.length;
            boolean[] results = new boolean[numCandidates];

            logger.debug("LoadBalancer:  PingTask executing [{}] servers configured", numCandidates);

            for (int i = 0; i < numCandidates; i++) {
                results[i] = false; /* Default answer is DEAD. */
                try {
                    // NOTE: IFF we were doing a real ping
                    // assuming we had a large set of servers (say 15)
                    // the logic below will run them serially
                    // hence taking 15 times the amount of time it takes
                    // to ping each server
                    // A better method would be to put this in an executor
                    // pool
                    // But, at the time of this writing, we dont REALLY
                    // use a Real Ping (its mostly in memory eureka call)
                    // hence we can afford to simplify this design and run
                    // this
                    // serially
                    if (ping != null) {
                        results[i] = ping.isAlive(servers[i]);
                    }
                } catch (Exception e) {
                    logger.error("Exception while pinging Server: '{}'", servers[i], e);
                }
            }
            return results;
        }
    }
}
