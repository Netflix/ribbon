package com.netflix.loadbalancer;

/**
 * A utility Ping Implementation that returns whatever its been set to return
 * (alive or dead)
 * @author stonse
 *
 */
public class PingConstant implements IPing {
		boolean constant = true;

		public void setConstant(String constantStr) {
				constant = (constantStr != null) && (constantStr.toLowerCase().equals("true"));
		}

		public void setConstant(boolean constant) {
				this.constant = constant;
		}

		public boolean getConstant() {
				return constant;
		}

		public boolean isAlive(Server server) {
				return constant;
		}
}
