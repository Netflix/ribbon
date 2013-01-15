package com.netflix.niws.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.client.NiwsClientConfig.NiwsClientConfigKey;
import com.google.common.util.concurrent.ThreadFactoryBuilder; 

/**
 * A default easy to use LoadBalancer that uses Discovery Client as its Ping
 *  and Instance discovering mechanism
 * @author stonse
 *
 */
public class NIWSDiscoveryLoadBalancer<T extends Server> extends AbstractNIWSLoadBalancer{
    private static final Logger LOGGER = LoggerFactory.getLogger(NIWSDiscoveryLoadBalancer.class);

		
		
	boolean isSecure = false;
	boolean useTunnel =  false;
	private Thread _shutdownThread;
	
	// to keep track of modification of server lists
	protected AtomicBoolean serverListUpdateInProgress = new AtomicBoolean(false);
	
	private static long LISTOFSERVERS_CACHE_UPDATE_DELAY = 1000; //msecs;
	private static long LISTOFSERVERS_CACHE_REPEAT_INTERVAL = 30*1000; //msecs; // every 30 secs
	// this is okay - its an inmemory call to Disocvery Client's cache
	
	private ScheduledThreadPoolExecutor _serverListRefreshExecutor = null;
	
	volatile AbstractNIWSServerList<T> niwsServerList;
	
    volatile AbstractNIWSServerListFilter<T> filter;  

    NiwsClientConfig niwsClientConfig;
    
    public static final String DEFAULT_SEVER_LIST_CLASS = "com.netflix.niws.client.DiscoveryEnabledNIWSServerList";
    	 
	public NIWSDiscoveryLoadBalancer(){
    	super();  
	}
    

    @Override
    public void initWithNiwsConfig(NiwsClientConfig niwsClientConfig) {        
        try {
            super.initWithNiwsConfig(niwsClientConfig);
            this.niwsClientConfig = niwsClientConfig;
            String niwsServerListClassName = niwsClientConfig.getProperty(NiwsClientConfigKey.NIWSServerListClassName,
                    DEFAULT_SEVER_LIST_CLASS).toString();
            Class<AbstractNIWSServerList <T>> niwsServerListClass = 
                (Class<AbstractNIWSServerList<T>>) Class.forName(niwsServerListClassName);
            
            niwsServerList = niwsServerListClass.newInstance();            
            niwsServerList.initWithNiwsConfig(niwsClientConfig);
            
            filter = niwsServerList.getFilterImpl(niwsClientConfig);
            filter.setLoadBalancerStats(getLoadBalancerStats());
            
            enableAndInitLearnNewServersFeature();
            
            updateListOfServers();
            if (this.isEnablePrimingConnections() && this.getPrimeConnections() != null) {
                this.getPrimeConnections().primeConnections(getServerList(true));
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Exception while initializing NIWSDiscoveryLoadBalancer:"
                            + niwsClientConfig.getClientName() + ", niwsClientConfig:"
                            + niwsClientConfig, e);
        }
    } 
	 
    @Override
    public void setServersList(List lsrv) {
        super.setServersList(lsrv); 
        List<T> serverList = (List<T>) lsrv; 
        Map<String, List<Server>> serversInZones = new HashMap<String, List<Server>>();
        for (Server server: serverList) {
            // make sure ServerStats is created to avoid creating them on hot path
            getLoadBalancerStats().getSingleServerStat(server);
            String zone = server.getZone();
            if (zone != null) {
                zone = zone.toLowerCase();
                List<Server> servers = serversInZones.get(zone);
                if (servers == null) {
                    servers = new ArrayList<Server>();
                    serversInZones.put(zone, servers);
                }
                servers.add(server);
            }
        }
        setServerListForZones(serversInZones);
    }

    // TODO: move zone server list to NIWSDiscoveryLoadBalancer
    protected void setServerListForZones(Map<String, List<Server>> zoneServersMap) {
        LOGGER.debug("Setting server list for zones: {}", zoneServersMap);
        getLoadBalancerStats().updateZoneServerMapping(zoneServersMap);
    }
            
    public AbstractNIWSServerList<T> getNIWSServerList() {
        return niwsServerList;
    }


    public void setNIWSServerList(
            AbstractNIWSServerList<T> niwsServerList) {
        this.niwsServerList = niwsServerList;
    }
    
    @Override
    public void setPing(IPing ping){
    	this.ping = ping;
    }
    
    @Override
    public void setRule(IRule rule){
    	this.rule = rule;
    }
    
	@Override
    /**
     * Makes no sense to ping an inmemory disc client
     * 
     */
	public void forceQuickPing() {
		//no-op
	}
    
    /**
	 * Feature that lets us add new instances (from AMIs) to the list of 
	 * existing servers that the LB will use 
	 * Call this method if you want this feature enabled
	 */
	public void enableAndInitLearnNewServersFeature(){
		String threadName = "NIWSDiscoveryLoadBalancer-" 
			+ getIdentifier();
		ThreadFactory factory = (new ThreadFactoryBuilder()).setDaemon(true).setNameFormat(threadName).build();
		_serverListRefreshExecutor = new ScheduledThreadPoolExecutor(1, factory);
		keepServerListUpdated();
		
		
		// Add it to the shutdown hook
		
		if(_shutdownThread == null){
			
			_shutdownThread = new Thread(new Runnable() {
				public void run()
				{
					LOGGER.info("Shutting down the Executor Pool for " + getIdentifier());
					shutdownExecutorPool();
				}
			});


			Runtime.getRuntime().addShutdownHook(_shutdownThread);
		}
	}

	private String getIdentifier() {
        return niwsClientConfig.getClientName();
    }
	
	private void keepServerListUpdated() {
		_serverListRefreshExecutor.scheduleAtFixedRate(
				new ServerListRefreshExecutorThread(), 
				LISTOFSERVERS_CACHE_UPDATE_DELAY, LISTOFSERVERS_CACHE_REPEAT_INTERVAL,
				TimeUnit.MILLISECONDS);
	}
	
	public void shutdownExecutorPool() {
		if (_serverListRefreshExecutor!=null){
			_serverListRefreshExecutor.shutdown();
			
			if(_shutdownThread != null){
				try{
					Runtime.getRuntime().removeShutdownHook(_shutdownThread);
				}catch(IllegalStateException ise){
					// this can happen if we're in the middle of a real shutdown, 
					// and that's 'ok'
				}
			}
			
		}
	}
	
	/**
	 * Class that updates the list of Servers 
	 * This is based on the method used by the client	 * 
	 * Appropriate Filters are applied before coming up 
	 * with the right set of servers
	 * @author stonse
	 *
	 */
	class ServerListRefreshExecutorThread implements Runnable {

	  public void run() {
		try {			
			updateListOfServers();
			
		} catch (Throwable e) {
			LOGGER.error(
							"Exception while updating List of Servers obtained from Discovery client",
							e);
			// e.printStackTrace();
		}
	  }
	
   }

	private void updateListOfServers() {
		List<T> servers = new ArrayList<T>();		
		if (niwsServerList!=null){
		    servers = niwsServerList.getUpdatedListOfServers();
		    LOGGER
            .debug("List of Servers obtained from Discovery client:"
                    + servers);
		   
		    if (filter != null) {
                servers = filter.getFilteredListOfServers(servers);
                        LOGGER
                                .debug("Filtered List of Servers obtained from Discovery client:"
                                        + servers);
		    }
		}		
		updateAllServerList(servers);		
	}
	
	/**
	 * Update the AllServer list in the LoadBalancer if necessary and enabled
	 * @param ls
	 */
	protected void updateAllServerList(List<T> ls) {
		// other threads might be doing this - in which case, we pass
		if (!serverListUpdateInProgress.get()) {
			   serverListUpdateInProgress.set(true);
				for (T s: ls){
					s.setAlive(true); //set so that clients can start using these servers right away instead
					// of having to wait out the ping cycle.
				}
				setServersList(ls);
				super.forceQuickPing();
				serverListUpdateInProgress.set(false);
		}		
	}		
	
	public String toString(){
	    StringBuilder sb = new StringBuilder("NIWSDiscoveryLoadBalancer:");
	    sb.append(super.toString());
	    sb.append("NIWSServerList:" + String.valueOf(niwsServerList));
	    return sb.toString();
	}
}
