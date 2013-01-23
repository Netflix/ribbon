/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
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
