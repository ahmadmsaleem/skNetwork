package sknetwork.proxy.core;

/**
 * The one thing the proxy half cannot do without its platform. Everything else a
 * backend asks for is routed to the server holding the player, which does the work
 * with its own Bukkit API.
 */
public interface ProxyActions {

	/** @return false if nobody by that name is on the proxy, or the server is unknown */
	boolean connect(String player, String server);
}
