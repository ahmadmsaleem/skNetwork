package sknetwork.proxy.core;

public interface ProxyActions {

	/** @return false if nobody by that name is on the proxy, or the server is unknown */
	boolean connect(String player, String server);
}
