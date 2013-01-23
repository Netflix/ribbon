package com.netflix.loadbalancer;

import com.netflix.niws.client.AbstractNIWSLoadBalancerPing;
import com.netflix.niws.client.IClientConfig;
import com.netflix.niws.client.IClientConfigAware;

/**
 * Default simple implementation
 * @author stonse
 *
 */
public class DummyPing extends AbstractNIWSLoadBalancerPing {
		
		public DummyPing() {
		}

		
		public boolean isAlive(Server server) {
			return true;
		}


		@Override
		public void initWithNiwsConfig(IClientConfig clientConfig) {
		}
}
