package com.netflix.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.niws.client.AbstractNIWSLoadBalancerPing;
import com.netflix.niws.client.AbstractNIWSLoadBalancerRule;
import com.netflix.niws.client.ClientFactory;
import com.netflix.niws.client.NIWSClientException;
import com.netflix.niws.client.NiwsClientConfig;
import com.netflix.niws.client.PrimeConnections;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.util.concurrent.ShutdownEnabledTimer;

/**
 * A Netflix Implementation of an ILoadBalancer 
 * @author stonse
 *
 */
public class NFLoadBalancer extends AbstractLoadBalancer implements PrimeConnections.PrimeConnectionListener {

    private static Logger logger = LoggerFactory.getLogger(NFLoadBalancer.class);
    private final static IRule DEFAULT_RULE = new RoundRobinRule();
    private static final String DEFAULT_NAME = "default";
    private static final String PREFIX = "LoadBalancer_";
    private static final IPing DEFAULT_PING = new DummyPing();

    protected IRule rule = DEFAULT_RULE;

    protected IPing ping = null;


    // TODO: server list should be updated at runtime
    @Monitor(name=PREFIX + "AllServerList", type=DataSourceType.INFORMATIONAL)
    protected volatile List<Server> allServerList = Collections.synchronizedList(new ArrayList<Server>());
    @Monitor(name=PREFIX + "UpServerList", type=DataSourceType.INFORMATIONAL)
    protected volatile List<Server> upServerList = Collections.synchronizedList(new ArrayList<Server>());

    protected ReadWriteLock allServerLock = new ReentrantReadWriteLock();
    protected ReadWriteLock upServerLock = new ReentrantReadWriteLock();


    protected String name = DEFAULT_NAME;


    protected Timer lbTimer = null;
    protected int pingIntervalSeconds = 10;
    protected int maxTotalPingTimeSeconds = 5;
    protected Comparator<Server> serverComparator = new ServerComparator();

    protected AtomicBoolean pingInProgress = new AtomicBoolean(false);

    protected LoadBalancerStats lbStats;

    private volatile Counter counter;
    
    private PrimeConnections primeConnections;
    
    private boolean enablePrimingConnections = false;
    
    /**
     * Default constructor. This constructor is mainly used by {@link ClientFactory}. Calling this constructor must 
     * be followed by calling {@link #init()} or {@link #initWithNiwsConfig(NiwsClientConfig)} to complete initialization.
     * When constructing programatically, use other constructors.
     */
    public NFLoadBalancer() {
        this.name = DEFAULT_NAME;
        this.ping = null;       
        setRule(DEFAULT_RULE);
        setupPingTask();
        lbStats = new LoadBalancerStats(DEFAULT_NAME);
        counter = createCounter();
    }

    public NFLoadBalancer(String lbName, IRule rule, LoadBalancerStats lbStats) {
        this(lbName, rule, lbStats, null);
    }

    public NFLoadBalancer(IPing ping, IRule rule) {
        this(DEFAULT_NAME, rule, new LoadBalancerStats(DEFAULT_NAME), ping);
    }

    public NFLoadBalancer(String name, IRule rule, LoadBalancerStats stats, IPing ping) {
        if (logger.isDebugEnabled()){
            logger.debug("LoadBalancer:  initialized");
        }
        this.name = name;
        this.ping = ping;       
        setRule(rule);
        setupPingTask();
        lbStats = stats;
        counter = createCounter();
        init();
    }

    public NFLoadBalancer(NiwsClientConfig config) {
        initWithNiwsConfig(config);
    }
    
    @Override
    public void initWithNiwsConfig(NiwsClientConfig clientConfig)  {
        String clientName = clientConfig.getClientName();
        String ruleClassName = (String) clientConfig.getProperty(
                    NiwsClientConfigKey.NFLoadBalancerRuleClassName);
        String pingClassName = (String) clientConfig.getProperty(
                    NiwsClientConfigKey.NFLoadBalancerPingClassName);

        AbstractNIWSLoadBalancerRule rule;
        AbstractNIWSLoadBalancerPing ping;
        try {
            rule = ClientFactory.instantiateNiwsConfigAwareClassInstance(ruleClassName, clientConfig);
            ping = ClientFactory.instantiateNiwsConfigAwareClassInstance(pingClassName, clientConfig);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing load balancer", e);
        }
        
        this.name = clientName;
        int pingIntervalTime = Integer.parseInt(""
                + clientConfig.getProperty(
                        NiwsClientConfigKey.NFLoadBalancerPingInterval, Integer
                                .parseInt("30")));
        int maxTotalPingTime = Integer.parseInt(""
                + clientConfig.getProperty(
                        NiwsClientConfigKey.NFLoadBalancerMaxTotalPingTime,
                        Integer.parseInt("2")));

        setPingInterval(pingIntervalTime);
        setMaxTotalPingTime(maxTotalPingTime);
        
        //cross associate with each other
        // i.e. Rule,Ping meet your container LB
        // LB, these are your Ping and Rule guys ...
        setRule(rule);
        setPing(ping);
        setLoadBalancerStats(new LoadBalancerStats(clientName));                
        rule.setLoadBalancer(this);
        ping.setLoadBalancer(this);
        logger.info("Client:" + name
                + " instantiated a LoadBalancer:" + toString());        
        boolean useTunnel = false;
        boolean enablePrimeConnections = false;
        if (clientConfig.getProperty(NiwsClientConfigKey.UseTunnel) != null) {
            useTunnel = Boolean.valueOf(String.valueOf(clientConfig.getProperty(NiwsClientConfigKey.UseTunnel)));
        }
        
        if (!useTunnel && clientConfig.getProperty(NiwsClientConfigKey.EnablePrimeConnections)!=null){
        	Boolean bEnablePrimeConnections = Boolean.valueOf(""+ clientConfig.getProperty(NiwsClientConfigKey.EnablePrimeConnections, "false"));
        	enablePrimeConnections = bEnablePrimeConnections.booleanValue();
        }

        if (enablePrimeConnections) {
            this.setEnablePrimingConnections(true);
            PrimeConnections primeConnections = new PrimeConnections(this.getName(), clientConfig);
            this.setPrimeConnections(primeConnections);
        }
        init();
    }

    private boolean canSkipPing() {
        if (ping == null || ping.getClass().getName().equals(DummyPing.class.getName())) {
            // default ping, no need to set up timer 
            return true;
        } else {
            return false;
        }        
    }
    
    private void setupPingTask() {
        if (canSkipPing()) {
            return;
        }
        if (lbTimer != null){
            lbTimer.cancel();
        }
        lbTimer = new ShutdownEnabledTimer("NFLoadBalancer-PingTimer-" + name, true);  
        lbTimer.schedule(new PingTask(), 0, pingIntervalSeconds*1000);
        forceQuickPing();
    }

    /**
     * Set the name for the load balancer. This should not be called since name should be 
     * immutable after initialization. Calling this method does not guarantee that all other data structures
     * that depend on this name will be changed accordingly.
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

    /* Don't forget to unlock. */
    public Lock lockAllServerList(boolean write) {
        Lock aproposLock = write ? allServerLock.writeLock() : allServerLock
                .readLock();
        aproposLock.lock();
        return aproposLock;
    }

    /* Don't forget to unlock. */
    public Lock lockUpServerList(boolean write) {
        Lock aproposLock = write ? upServerLock.writeLock() : upServerLock
                .readLock();
        aproposLock.lock();
        return aproposLock;
    }

    /* How often do we ping the servers? */
    public void setPingInterval(int pingIntervalSeconds) {
        if (pingIntervalSeconds < 1) {
            return;
        }

        this.pingIntervalSeconds = pingIntervalSeconds;
        if (logger.isDebugEnabled()){
            logger.debug("LoadBalancer:  pingIntervalSeconds set to "
                    + this.pingIntervalSeconds);
        }
        setupPingTask(); // since ping data changed
    }

    public int getPingInterval() {
        return pingIntervalSeconds;
    }

    /*
     * How long until 'pencils down'? That is, how long can each ping cycle last
     * before ALL remaining pings are declared 'dead' ?
     */
    public void setMaxTotalPingTime(int maxTotalPingTimeSeconds) {
        if (maxTotalPingTimeSeconds < 1) {
            return;
        }
        this.maxTotalPingTimeSeconds = maxTotalPingTimeSeconds;
        if (logger.isDebugEnabled()){
            logger.debug("LoadBalancer: maxTotalPingTime set to "
                    + this.maxTotalPingTimeSeconds);
        }

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
            if (!ping.equals(this.ping)){
                this.ping = ping;
                setupPingTask(); // since ping data changed
            }
        } else{
            this.ping = null;
            //cancel the timer task
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
    }

    /*
     * Does not lock, so might be inconsistent with reality depending on list
     * implementation and what's going on
     */
    public int getServerCount(boolean onlyAvailable) {
        if (onlyAvailable) {
            return upServerList.size();
        } else {
            return allServerList.size();
        }
    }

    /*
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
            } catch (Exception e){
                logger.error("Exception while adding a newServer", e);
            }
        }
    }

    /*
     * Add a list of servers to the 'allServer' list; does not verify
     * uniqueness, so you could give a server a greater share by adding it more
     * than once
     */
    public void addServers(List<Server> newServers) {
        if (newServers != null && newServers.size() > 0) {			
            try {
                newServers.addAll(allServerList);				
                setServersList(newServers);
            } catch (Exception e){
                logger.error("Exception while adding Servers", e);
            }
        }
    }

    /*
     * Add a list of servers to the 'allServer' list; does not verify
     * uniqueness, so you could give a server a greater share by adding it more
     * than once
     * USED by Test Cases only for legacy reason. DO NOT USE!!
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
            } catch (Exception e){
                logger.error("Exception while adding Servers", e);
            }
        }
    }

    /* List form. SETS, does not add */
    public void setServersList(List lsrv) {
        Lock writeLock = allServerLock.writeLock();
        if (logger.isDebugEnabled()){
            logger.debug("LoadBalancer:  clearing server list (SET op)");
        }
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
                    if (logger.isDebugEnabled()){
                        logger.debug("LoadBalancer:  addServer ["
                                + ((Server) server).getId() + "]");
                    }
                    allServers.add((Server) server);
                }else{
                    throw new IllegalArgumentException("Type String or Server expected, instead found:" + server.getClass());
                }

            }
            boolean listChanged = false;
            if (!allServerList.equals(allServers)){
                listChanged = true;
            }
            if (enablePrimingConnections) {
                for (Server server: allServers) {
                    if (!allServerList.contains(server)) {
                        server.setReadyToServe(false);
                        newServers.add((Server) server);
                    }
                }
                if (primeConnections != null) {
                    primeConnections.primeConnectionsAsync(newServers, this);
                }
            }
            // This will reset readyToServe flag to true on all servers regardless whether 
            // previous priming connections are success or not
            allServerList = allServers;
            if (canSkipPing()) {
                for (Server s: allServerList) {
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
                logger.error("Exception while adding Servers", e);
            }
        }
    }



    /* Non-locking version; get a server by 0-based index in list */
    public Server getServerByIndex(int index, boolean availableOnly) {
        try {
            return (availableOnly ? upServerList.get(index) : allServerList
                    .get(index));
        } catch (Exception e) {
            return null;
        }
    }


    public List<Server> getServerList(boolean availableOnly) {
        return (availableOnly ? upServerList : allServerList);
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

    public void cancelPingTask(){
        if(lbTimer != null){
            lbTimer.cancel();
        }
    }

    /**
     * TimerTask that keeps runs every X seconds to check the status of each server/node
     * in the Server List	
     * @author stonse
     *
     */
    class PingTask extends TimerTask {
        public void run() {
            Pinger ping = new Pinger();
            try {
                ping.runPinger();
            } catch (Throwable t) {
                logger.error("Throwable caught while running extends for " + name, t);
            }
        }
    }


    class Pinger{

        public void runPinger(){

            if (pingInProgress.get()){
                return; // Ping in progress - nothing to do
            }else{
                pingInProgress.set(true);
            }

            // we are "in" - we get to Ping

            Object[] allServers = null;
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
                allServers = allServerList.toArray();
                allLock.unlock();

                int numCandidates = allServers.length;
                results = new boolean[numCandidates];

                if (logger.isDebugEnabled()){
                    logger.debug("LoadBalancer:  PingTask executing ["
                            + numCandidates + "] servers configured");
                }

                for (int i = 0; i < numCandidates; i++) {
                    results[i] = false; /* Default answer is DEAD. */
                    try {
                        // NOTE: IFF we were doing a real ping
                        // assuming we had a large set of servers (say 15)
                        // the logic below will run them serially 
                        // hence taking 15 times the amount of time it takes
                        // to ping each server
                        // A better method would be to put this in an executor pool
                        // But, at the time of this writing, we dont REALLY
                        // use a Real Ping (its mostly in memory discovery call)
                        // hence we can afford to simplify this design and run this 
                        // serially
                        if (ping!=null){
                            results[i] = ping.isAlive((Server) allServers[i]);
                        }
                    } catch (Throwable t) {
                        logger.error("Exception while pinging Server:" + allServers[i], t);
                    }
                }

                ArrayList<Server> newUpList = new ArrayList<Server>();

                for (int i = 0; i < numCandidates; i++) {
                    boolean isAlive = results[i];
                    Server svr = (Server) allServers[i];
                    boolean oldIsAlive = svr.isAlive();

                    svr.setAlive(isAlive);

                    if (oldIsAlive != isAlive && logger.isDebugEnabled()){
                    	logger.debug("LoadBalancer:  Server [" + svr.getId()
                    			+ "] status changed to "
                    			+ (isAlive ? "ALIVE" : "DEAD"));
                    }

                    if (isAlive) {
                        newUpList.add(svr);
                    }
                }
                // System.out.println(count + " servers alive");
                upLock = upServerLock.writeLock();
                upLock.lock();
                upServerList = newUpList;
                upLock.unlock();
            } catch (Throwable t) {
                logger.error("Throwable caught while running the Pinger-" + name, t);
            } finally {               
                pingInProgress.set(false);               
            }
        }
    }

    private final Counter createCounter() {
        return Monitors.newCounter("LoadBalancer_ChooseServer");
    }
    
    /*
     * Get the alive server dedicated to key
     * 
     * @return the decicated server
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
                return rule.choose(this, key);
            } catch (Throwable t) {
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
                Server svr = rule.choose(this, key);
                return ((svr == null) ? null : svr.getId());
            } catch (Throwable t) {
                return null;
            }
        }
    }

    public void markServerDown(Server server) {
        if (server == null) {
            return;
        }

        if (!server.isAlive()) {
            return;
        }

        logger.error("LoadBalancer:  markServerDown called on ["
                + server.getId() + "]");
        server.setAlive(false);
        //forceQuickPing();
    }

    public void markServerDown(String id) {
        boolean triggered = false;

        id = Server.normalizeId(id);

        if (id == null) {
            return;
        }

        Lock writeLock = upServerLock.writeLock();

        try {

            for (Server svr : upServerList) {
                if (svr.isAlive() && (svr.getId().equals(id))) {
                    triggered = true;
                    svr.setAlive(false);
                }
            }

            if (triggered) {
                logger.error("LoadBalancer:  markServerDown called on ["
                        + id + "]");
                //forceQuickPing();
            }

        } finally {
            try {
                writeLock.unlock();
            } catch (Exception e) { // NOPMD
            }
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
        if (logger.isDebugEnabled()){
            logger.debug("LoadBalancer:  forceQuickPing invoked");
        }
        Pinger ping = new Pinger();
        try {
            ping.runPinger();
        } catch (Throwable t) {
            logger.error("Throwable caught while running forceQuickPing() for " + name, t);
        }
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("{NFLoadBalancer:name=").append(this.getName()).
        append(",current list of Servers=").append(this.allServerList).
        append(",Load balancer stats=").append(this.lbStats.toString()).
        append("}");
        return sb.toString();
    }

    protected void init() {
        Monitors.registerObject("LoadBalancer_" + name, this);
        if (enablePrimingConnections && primeConnections != null) {
            primeConnections.primeConnections(getServerList(true));
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

    public final boolean isEnablePrimingConnections() {
        return enablePrimingConnections;
    }

    public final void setEnablePrimingConnections(boolean enablePrimingConnections) {
        this.enablePrimingConnections = enablePrimingConnections;
    }
}
