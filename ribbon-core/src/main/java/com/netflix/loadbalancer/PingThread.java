package com.netflix.loadbalancer;

import java.util.concurrent.atomic.AtomicInteger;

public class PingThread extends Thread {
		int           index;
		Object[]      allServers;
		boolean[]     results;
		AtomicInteger resultCounter;
		IPing         ping;

		public PingThread() {
		}

		public PingThread(int index, Object[] allServers, 
											boolean[] results, AtomicInteger resultCounter,
											IPing ping) {
				this.index = index;
				this.allServers = allServers;
				this.results = results;
				this.resultCounter = resultCounter;
				this.ping = ping;
		}

		public void run() {
				try {
						results[index] = ping.isAlive((Server) allServers[index]);
				} catch (Throwable t) {
						results[index] = false;
				} finally {
						resultCounter.incrementAndGet();
				}
		}
}
