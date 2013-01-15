package com.netflix.loadbalancer;

import java.util.Timer;
import java.util.TimerTask;

public class InterruptTask extends TimerTask {
		
		static Timer timer = new Timer("InterruptTimer", true); 
		
		protected Thread target = null;

		public InterruptTask(long millis) {
				target = Thread.currentThread();
				timer.schedule(this, millis);
		}


		/* Auto-scheduling constructor */
		public InterruptTask(Thread target, long millis) {
				this.target = target;
				timer.schedule(this, millis);
		}


		public boolean cancel() {
				try {
						/* This shouldn't throw exceptions, but... */
						return super.cancel();
				} catch (Exception e) {
						return false;
				}
		}

		public void run() {
				if ((target != null) && (target.isAlive())) {
						target.interrupt();
				}
		}
} 
