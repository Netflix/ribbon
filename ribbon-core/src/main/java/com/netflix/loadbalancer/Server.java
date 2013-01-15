package com.netflix.loadbalancer;


public class Server {

    public static final String UNKNOWN_ZONE = "UNKNOWN";
	String host;
	int port = 80;
	String id;
	boolean isAliveFlag;
    private String zone = UNKNOWN_ZONE;
    
    private volatile boolean readyToServe = true;

	public Server(String host, int port) {
		this.host = host;
		this.port = port;
		this.id = host + ":" + port;
		isAliveFlag = false;
	}

	/* host:port combination */
	public Server(String id) {
		setId(id);
		isAliveFlag = false;
	}

	// No reason to synchronize this, I believe.
	// The assignment should be atomic, and two setAlive calls
	// with conflicting results will still give nonsense(last one wins)
	// synchronization or no.

	public void setAlive(boolean isAliveFlag) {
		this.isAliveFlag = isAliveFlag;
	}

	public boolean isAlive() {
		return isAliveFlag;
	}

	public void setHostPort(String hostPort) {
		setId(hostPort);
	}

	static public String normalizeId(String id) {
		if (id != null) {
			String host = null;
			int port = 80;

			if (id.toLowerCase().startsWith("http://")) {
				id = id.substring(7);
			} else if (id.toLowerCase().startsWith("https://")) {
				id = id.substring(8);
			}

			if (id.contains("/")) {
				int slash_idx = id.indexOf("/");
				id = id.substring(0, slash_idx);
			}

			int colon_idx = id.indexOf(':');

			if (colon_idx == -1) {
				host = id; // default
				port = 80;
			} else {
				host = id.substring(0, colon_idx);
				try {
					port = Integer.valueOf(id.substring(colon_idx + 1));
				} catch (NumberFormatException e) {
					throw e;
				}
			}

			if (null == host) {
				return null;
			}

			return (host + ":" + port);
		} else {
			return null;
		}
	}

	public void setId(String id) {
		this.id = normalizeId(id);
	}

	public void setPort(int port) {
		this.port = port;

		if (host != null) {
			id = host + ":" + port;
		}
	}

	public void setHost(String host) {
		if (host != null) {
			this.host = host;
			id = host + ":" + port;
		}
	}

	public String getId() {
		return id;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getHostPort() {
		return host + ":" + port;
	}
	
	public String toString(){
		return this.getId();
	}
	
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Server))
			return false;
		Server svc = (Server) obj;
		return svc.getId().equals(this.getId());

	}

	public int hashCode() {
		int hash = 7;	
		hash = 31 * hash + (null == this.getId() ? 0 : this.getId().hashCode());
		return hash;
	}

    public final String getZone() {
        return zone;
    }

    public final void setZone(String zone) {
        this.zone = zone;
    }

    public final boolean isReadyToServe() {
        return readyToServe;
    }

    public final void setReadyToServe(boolean readyToServe) {
        this.readyToServe = readyToServe;
    }


}
