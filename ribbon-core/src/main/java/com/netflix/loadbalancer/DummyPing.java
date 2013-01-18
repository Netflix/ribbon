package com.netflix.loadbalancer;

import com.netflix.niws.client.AbstractNIWSLoadBalancerPing;
import com.netflix.niws.client.NiwsClientConfig;
import com.netflix.niws.client.NiwsClientConfigAware;

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
		public void initWithNiwsConfig(NiwsClientConfig niwsClientConfig) {
		}
}
