package com.netflix.loadbalancer;

import java.util.Random;

public class RandomRule implements IRule {
	Random rand;

	public RandomRule() {
		rand = new Random();
	}

	/*
	 * Randomly choose from all living servers
	 */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE")
	public Server choose(NFLoadBalancer lb, Object key) {
		if (lb == null) {
			return null;
		}
		Server server = null;

		while (server == null) {
			if (Thread.interrupted()) {
				return null;
			}

			int serverCount = lb.getServerCount(true);
			if (serverCount == 0) {
				/*
				 * No servers. End regardless of pass, because subsequent passes
				 * only get more restrictive.
				 */
				return null;
			}

			int index = rand.nextInt(serverCount);
			server = lb.getServerByIndex(index, true);

			if (server == null) {
				/*
				 * The only time this should happen is if the server list were
				 * somehow trimmed. This is a transient condition. Retry after
				 * yielding.
				 */
				Thread.yield();
				continue;
			}

			if (server.isAlive()) {
				return (server);
			}

			// Shouldn't actually happen.. but must be transient or a bug.
			server = null;
			Thread.yield();
		}

		return server;

	}
}
