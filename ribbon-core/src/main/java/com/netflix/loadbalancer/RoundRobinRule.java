package com.netflix.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Not perfect Round Robin, although it should be pretty close
 * if the loadbalancer isn't changed.
 */
public class RoundRobinRule implements IRule{
    AtomicInteger nextIndexAI;

    private static Logger log = LoggerFactory.getLogger(RoundRobinRule.class);
    
    public RoundRobinRule () {
				nextIndexAI = new AtomicInteger(0);
    }

    /*
		 * Rotate over all known servers.
     */
		final static boolean availableOnly = false;
		public Server choose(NFLoadBalancer lb, Object key) {
		    if (lb == null) {
		        log.warn("no load balancer");
		        return null;
		    }
		    Server server = null;
		    int    index  = 0;

		    int count = 0;
		    while (server == null && count++ < 10) {
		        int upCount     = lb.getServerCount(true);
		        int serverCount = lb.getServerCount(availableOnly);

		        if ((upCount == 0) || (serverCount == 0)) {
		            log.warn("No up servers available from load balancer: " + lb);
		            return null;
		        }

		        index  = nextIndexAI.incrementAndGet() % serverCount;
		        server = lb.getServerByIndex(index, availableOnly);


		        if (server == null) {
		            /* Transient. */
		            Thread.yield();
		            continue;
		        }


		        if(server.isAlive() && (!lb.isEnablePrimingConnections() || server.isReadyToServe())) {
		            return(server);
		        }

		        // Next.
		        server = null;
		    }

		    if (count >= 10) {
		        log.warn("No available alive servers after 10 tries from load balancer: " + lb);
		    }
		    return server;
		}
}
