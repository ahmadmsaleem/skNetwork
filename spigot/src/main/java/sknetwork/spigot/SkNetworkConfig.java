package sknetwork.spigot;

import sknetwork.common.Durations;
import sknetwork.common.Protocol;
import sknetwork.common.SkNetwork;
import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once and typed. */
final class SkNetworkConfig {

	private final String host;
	private final int port;
	private final String token;
	private final String serverName;
	private final String prefix;
	private final boolean forceSkriptConfig;
	private final boolean displayValues;
	private final boolean checkForUpdates;
	private final long atomicTimeoutMs;

	SkNetworkConfig(FileConfiguration config, int serverPort) {
		this.host = config.getString("proxy.host", "127.0.0.1");
		this.port = config.getInt("proxy.port", Protocol.DEFAULT_PORT);
		this.token = config.getString("proxy.token", "change-me");
		this.prefix = config.getString("prefix", SkNetwork.DEFAULT_PREFIX);
		this.forceSkriptConfig = config.getBoolean("force-skript-config", true);
		this.displayValues = config.getBoolean("display-values", true);
		this.checkForUpdates = config.getBoolean("check-for-updates", true);
		this.atomicTimeoutMs = Durations.millis(config.getString("atomic-timeout"), 5_000);

		String name = config.getString("server-name", "");
		this.serverName = name == null || name.isBlank() ? "server-" + serverPort : name;
	}

	String host() {
		return host;
	}

	int port() {
		return port;
	}

	String token() {
		return token;
	}

	String serverName() {
		return serverName;
	}

	String prefix() {
		return prefix;
	}

	boolean forceSkriptConfig() {
		return forceSkriptConfig;
	}

	/** Whether writes carry a readable copy of the value for the proxy's /sknetproxy dump. */
	boolean displayValues() {
		return displayValues;
	}

	/** Whether to ask GitHub at startup if there is a newer skNetwork. */
	boolean checkForUpdates() {
		return checkForUpdates;
	}

	/** How long 'and wait' parks a trigger before giving up on the proxy. */
	long atomicTimeoutMs() {
		return atomicTimeoutMs;
	}

	/**
	 * Names travel without the prefix, so servers configured with different
	 * prefixes still share data.
	 *
	 * @return the wire name, or null if the name lacks our prefix
	 */
	String stripPrefix(String name) {
		return name.startsWith(prefix) ? name.substring(prefix.length()) : null;
	}
}
