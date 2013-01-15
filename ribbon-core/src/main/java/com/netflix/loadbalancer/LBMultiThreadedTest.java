package com.netflix.loadbalancer;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class that simulates a multi-thread user request environment to test
 * faked server alive/dead scenario and LB performance
 * TODO: Move this to junit test 
 * @author stonse
 *
 */
public class LBMultiThreadedTest {
	static int numServers = 10;
	static int serverDie = 3; /* out of numServers */
	static int serverRevive = 2; /* out of 10000 */
	static int megadeathEvent = 1; /* out of 10000 */
	static int numRequestsPerSimulUser = 100;
	static int numSimulUser = 100;
	static int numRequests = numRequestsPerSimulUser * numSimulUser;

	static final int oddDenom = 10;

	static Server[] allServers = new Server[numServers];
	static HashMap<String, Boolean> isAliveMap = new HashMap<String, Boolean>();
	static ServerComparator serverComparator = new ServerComparator();

	public static void main(String[] argv) {
		for (int i = 0; i < numServers; i++) {
			allServers[i] = new Server("" + i, 80);
			isAliveMap.put(allServers[i].getId(), new Boolean(true));
		}

		Thread upDownThread = new UpDownThread();
		upDownThread.setDaemon(true);
		upDownThread.start();

		NFLoadBalancer lb = new NFLoadBalancer();
		lb.setPingInterval(20);
		lb.setMaxTotalPingTime(5);

		lb.setPing(new PingFake());

		//lb.setRule(new RetryRule(new CustomerHashRule(), 200));
		//lb.setRule(new RoundRobinRule());
		lb.setRule(new RetryRule(new RoundRobinRule(), 200));
		
		lb.addServers(allServers);

		System.out.println("Zzzz");
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
		}

		lb.forceQuickPing();

		AtomicInteger[] timesChosen = new AtomicInteger[numServers];
		AtomicInteger failedCount = new AtomicInteger(0);
		AtomicInteger threadsDone = new AtomicInteger(0);
		AtomicInteger totalUsers = new AtomicInteger(0);
		AtomicInteger totalSwitches = new AtomicInteger(0);

		for (int i = 0; i < numServers; i++) {
			timesChosen[i] = new AtomicInteger(0);
		}

		Thread[] userThreads = new Thread[numSimulUser];
		for (int i = 0; i < numSimulUser; i++) {
			userThreads[i] = new UserThread(lb, numRequestsPerSimulUser,
					threadsDone, failedCount, timesChosen, totalUsers,
					totalSwitches);
		}

		System.out.println("Starting user threads");
		long startTime = System.currentTimeMillis();

		for (Thread userThread : userThreads) {
			userThread.start();
		}

		while (threadsDone.get() < numSimulUser) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
		}
		long stopTime = System.currentTimeMillis();
		long elapsedTime = stopTime - startTime;

		double avt = (elapsedTime * 1.0) / numRequests;
		System.out.println("\nFinished " + numRequests + " requests -- "
				+ elapsedTime + " ms (avg " + avt + ")\n");

		System.out.println("-------------------");
		System.out.println("Simul users:     " + numSimulUser);
		System.out.println("Total users:     " + totalUsers);
		System.out.println("Switch/Fail:   " + totalSwitches);
		System.out.println("SF/US            " + (totalSwitches.intValue())
				/ (1.0 * totalUsers.intValue()));

		System.out.println("Total failures:  " + failedCount.intValue());

		for (int i = 0; i < numServers; i++) {
			System.out.println("fakeserve" + i + "\t"
					+ timesChosen[i].intValue());
		}

	}

	static class UpDownThread extends Thread {
		Random rand = new Random();

		public void run() {
			while (true) {
				if (allServers != null) {
					if (rand.nextInt(oddDenom) <= megadeathEvent) {
						/* it's the end of the world as we know it */
						for (Server server : allServers) {
							isAliveMap.put(server.getId(), new Boolean(false));
						}
					} else {
						for (Server server : allServers) {
							String hostPortServlet = server.getId();
							Boolean currentStatus = isAliveMap
									.get(hostPortServlet);
							Boolean newStatus = null;

							if (currentStatus == null) {
								currentStatus = new Boolean(true);
							}

							if (currentStatus) {
								newStatus = new Boolean(
										rand.nextInt(oddDenom) >= serverDie);
							} else {
								newStatus = new Boolean(
										rand.nextInt(oddDenom) < serverRevive);
							}

							isAliveMap.put(hostPortServlet, newStatus);
						}
					}
				} else {
					Thread.yield();
				}
			}
		}
	}

	/**
	 * Simulates a User making a Request
	 * @author stonse
	 *
	 */
	static class UserThread extends Thread {
		int numRequests = 0;
		NFLoadBalancer lb;
		Random rand = new Random();
		int requestIdx = 0;
		AtomicInteger threadsDone;
		AtomicInteger failedCount;

		AtomicInteger totalUsers;
		AtomicInteger totalSwitches;

		AtomicInteger[] timesChosen;

		byte[] fakeCustomerBytes = new byte[4];
		IRule ir = null;

		public UserThread(NFLoadBalancer lb, int numRequests,
				AtomicInteger threadsDone, AtomicInteger failedCount,
				AtomicInteger[] timesChosen, AtomicInteger totalUsers,
				AtomicInteger totalSwitches) {
			this.lb = lb;
			this.numRequests = numRequests;
			this.failedCount = failedCount;
			this.timesChosen = timesChosen;
			this.threadsDone = threadsDone;
			this.totalUsers = totalUsers;
			this.totalSwitches = totalSwitches;
			ir = lb.getRule();
		}

		public void run() {
			String fakeCustomer = null;
			int requestsLeft = 0;
			Server prevChoice = null;
			boolean newUser = false;

			for (int i = 0; i < numRequests; i++) {
				if (fakeCustomer == null) {
					requestIdx += rand.nextInt();
					fakeCustomerBytes[0] = (byte) (requestIdx % 256);
					fakeCustomerBytes[1] = (byte) ((requestIdx >> 8) % 256);
					fakeCustomerBytes[2] = (byte) ((requestIdx >> 16) % 256);
					fakeCustomerBytes[3] = (byte) ((requestIdx >> 24) % 256);
					fakeCustomer = new String(fakeCustomerBytes);

					/* variable number of requests per "user" */
					requestsLeft = (rand.nextInt(3) + 1)
							* (rand.nextInt(3) + 1) * (rand.nextInt(10) + 1);
					totalUsers.getAndIncrement();
					prevChoice = null;
					newUser = true;
				}

				boolean failed = false;
				boolean switched = false;
				Server choice = lb.chooseServer(fakeCustomer);

				if (!newUser) {
					if (prevChoice == null) {
						switched = true;
					} else if ((choice != null)
							&& (serverComparator.compare(choice, prevChoice) != 0)) {
						totalSwitches.getAndIncrement();
						switched = true;
					}
				}
				newUser = false;
				prevChoice = choice;

				if (--requestsLeft == 0) {
					/**
					 * Create a StikyRule and then uncomment this ... if (ir
					 * instanceof StickyRule) { ((StickyRule)
					 * ir).clearAssignment(fakeCustomer); }
					 **/

					fakeCustomer = null;
				}

				if ((choice == null) || (!choice.isAlive())
						|| (!isAliveMap.get(choice.getId()).booleanValue())) {
					failed = true;

					if (choice != null) {
						/* notify load balancer */
						lb.markServerDown(choice);
					}
				} else {
					int hostNum = Integer.parseInt(choice.getHost());
					timesChosen[hostNum].getAndIncrement();
				}

				if (failed) {
					failedCount.getAndIncrement();
				}

				if (switched || failed) {
					totalSwitches.getAndIncrement();
				}
			}
			threadsDone.getAndIncrement();
		}
	}

	static class PingFake implements IPing {
		public boolean isAlive(Server server) {
			Boolean res = isAliveMap.get(server.getId());
			return ((res != null) && (res.booleanValue()));
		}
	}
}
