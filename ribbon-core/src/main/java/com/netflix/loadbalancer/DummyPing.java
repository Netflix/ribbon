package com.netflix.loadbalancer;

/**
 * Default simple implementation
 * @author stonse
 *
 */
public class DummyPing implements IPing {
		
		public DummyPing() {
		}

		
		public boolean isAlive(Server server) {
			return true;
		}
}
